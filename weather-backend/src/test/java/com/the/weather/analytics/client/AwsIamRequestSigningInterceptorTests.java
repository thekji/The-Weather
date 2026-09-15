package com.the.weather.analytics.client;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.regions.Region;

class AwsIamRequestSigningInterceptorTests {

    @Test
    void signsApiGatewayRequestsWithTheConfiguredRegionAndTemporaryCredentials() {
        RestClient.Builder builder = RestClient.builder()
                .requestInterceptor(new AwsIamRequestSigningInterceptor(
                        StaticCredentialsProvider.create(AwsSessionCredentials.create(
                                "AKIDEXAMPLE",
                                "secret-key-for-test-only",
                                "session-token-for-test-only")),
                        Region.AP_SOUTHEAST_2,
                        Clock.fixed(Instant.parse("2026-09-15T08:45:00Z"), ZoneOffset.UTC),
                        AwsV4HttpSigner.create()));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(once(), requestTo("https://gateway.test/analytics/summary"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        containsString(
                                "Credential=AKIDEXAMPLE/20260915/ap-southeast-2/execute-api/aws4_request")))
                .andExpect(header("X-Amz-Date", "20260915T084500Z"))
                .andExpect(header("X-Amz-Security-Token", "session-token-for-test-only"))
                .andRespond(withSuccess());

        builder.build()
                .get()
                .uri("https://gateway.test/analytics/summary")
                .retrieve()
                .toBodilessEntity();

        server.verify();
    }
}
