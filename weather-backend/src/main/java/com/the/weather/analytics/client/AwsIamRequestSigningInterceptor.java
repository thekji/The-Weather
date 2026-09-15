package com.the.weather.analytics.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.regions.Region;

/** Signs internal API Gateway calls with the ECS task role using SigV4. */
final class AwsIamRequestSigningInterceptor implements ClientHttpRequestInterceptor {

    private static final String SIGNING_NAME = "execute-api";
    private static final Set<String> GENERATED_SIGNING_HEADERS = Set.of(
            "authorization",
            "x-amz-content-sha256",
            "x-amz-date",
            "x-amz-security-token");

    private final AwsCredentialsProvider credentialsProvider;
    private final Region region;
    private final Clock clock;
    private final AwsV4HttpSigner signer;

    AwsIamRequestSigningInterceptor(
            AwsCredentialsProvider credentialsProvider,
            Region region) {
        this(credentialsProvider, region, Clock.systemUTC(), AwsV4HttpSigner.create());
    }

    AwsIamRequestSigningInterceptor(
            AwsCredentialsProvider credentialsProvider,
            Region region,
            Clock clock,
            AwsV4HttpSigner signer) {
        this.credentialsProvider = credentialsProvider;
        this.region = region;
        this.clock = clock;
        this.signer = signer;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        SdkHttpRequest signedRequest = sign(request, body);
        signedRequest.headers().forEach((name, values) -> {
            if (GENERATED_SIGNING_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                request.getHeaders().put(name, new ArrayList<>(values));
            }
        });
        return execution.execute(request, body);
    }

    private SdkHttpRequest sign(HttpRequest request, byte[] body) throws IOException {
        try {
            Map<String, List<String>> headers = new LinkedHashMap<>();
            request.getHeaders().forEach(
                    (name, values) -> headers.put(name, new ArrayList<>(values)));
            SdkHttpFullRequest unsignedRequest = SdkHttpFullRequest.builder()
                    .uri(request.getURI())
                    .method(SdkHttpMethod.fromValue(request.getMethod().name()))
                    .headers(headers)
                    .contentStreamProvider(() -> new ByteArrayInputStream(body))
                    .build();
            SignedRequest signedRequest = signer.sign(signRequest -> signRequest
                    .identity(credentialsProvider.resolveCredentials())
                    .request(unsignedRequest)
                    .payload(() -> new ByteArrayInputStream(body))
                    .putProperty(AwsV4FamilyHttpSigner.SERVICE_SIGNING_NAME, SIGNING_NAME)
                    .putProperty(AwsV4HttpSigner.REGION_NAME, region.id())
                    .putProperty(HttpSigner.SIGNING_CLOCK, clock));
            return signedRequest.request();
        } catch (RuntimeException exception) {
            throw new IOException("Could not authorize the analytics API Gateway request.", exception);
        }
    }
}
