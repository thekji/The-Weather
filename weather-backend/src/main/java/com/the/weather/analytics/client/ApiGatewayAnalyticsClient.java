package com.the.weather.analytics.client;

import com.the.weather.analytics.dto.AnalyticsRefreshResponseDto;
import com.the.weather.analytics.dto.AnalyticsRefreshStatus;
import com.the.weather.analytics.dto.AnalyticsRefreshStatusDto;
import com.the.weather.analytics.dto.AnalyticsSummaryDto;
import com.the.weather.analytics.dto.LocationPostCountDto;
import com.the.weather.analytics.dto.RatingDistributionDto;
import com.the.weather.analytics.service.AnalyticsService;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Service
public class ApiGatewayAnalyticsClient implements AnalyticsService {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(35);

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiGatewayAnalyticsClient.class);
    private static final String CONFIGURATION_ERROR =
            "Analytics API Gateway is not configured.";
    private static final String NO_DATA_ERROR =
            "No analytics data is available yet.";
    private static final String REFRESH_ERROR =
            "Could not export the latest analytics data.";
    private static final String STATUS_ERROR =
            "Could not update the analytics catalogue.";
    private static final String SUMMARY_ERROR =
            "Could not load analytics.";

    private final RestClient restClient;
    private final String refreshUrl;
    private final String refreshStatusUrl;
    private final String summaryUrl;

    @Autowired
    public ApiGatewayAnalyticsClient(
            AwsCredentialsProvider credentialsProvider,
            @Value("${aws.region}") String awsRegion,
            @Value("${analytics.refresh-url:}") String refreshUrl,
            @Value("${analytics.refresh-status-url:}") String refreshStatusUrl,
            @Value("${analytics.summary-url:}") String summaryUrl) {
        this(
                configuredRestClient(credentialsProvider, Region.of(awsRegion)),
                refreshUrl,
                refreshStatusUrl,
                summaryUrl);
    }

    ApiGatewayAnalyticsClient(
            RestClient restClient,
            String refreshUrl,
            String refreshStatusUrl,
            String summaryUrl) {
        this.restClient = restClient;
        this.refreshUrl = refreshUrl;
        this.refreshStatusUrl = refreshStatusUrl;
        this.summaryUrl = summaryUrl;
    }

    @Override
    public AnalyticsRefreshResponseDto refresh() {
        URI uri = configuredUri(refreshUrl);
        AnalyticsRefreshResponseDto response = execute(
                () -> restClient.post()
                        .uri(uri)
                        .retrieve()
                        .body(AnalyticsRefreshResponseDto.class),
                "refresh",
                REFRESH_ERROR,
                false);
        if (response == null
                || response.status() != AnalyticsRefreshStatus.REFRESHING
                || !StringUtils.hasText(response.message())) {
            throw invalidResponse(REFRESH_ERROR);
        }
        return response;
    }

    @Override
    public AnalyticsRefreshStatusDto refreshStatus() {
        URI uri = configuredUri(refreshStatusUrl);
        AnalyticsRefreshStatusDto response = execute(
                () -> restClient.get()
                        .uri(uri)
                        .retrieve()
                        .body(AnalyticsRefreshStatusDto.class),
                "status",
                STATUS_ERROR,
                false);
        if (response == null || response.status() == null) {
            throw invalidResponse(STATUS_ERROR);
        }
        return response;
    }

    @Override
    public AnalyticsSummaryDto summary() {
        URI uri = configuredUri(summaryUrl);
        AnalyticsSummaryDto response = execute(
                () -> restClient.get()
                        .uri(uri)
                        .retrieve()
                        .body(AnalyticsSummaryDto.class),
                "summary",
                SUMMARY_ERROR,
                true);
        if (!isValid(response)) {
            throw invalidResponse(SUMMARY_ERROR);
        }
        return response;
    }

    private <T> T execute(
            Supplier<T> request,
            String operation,
            String publicMessage,
            boolean notFoundMeansNoData) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            int downstreamStatus = exception.getStatusCode().value();
            LOGGER.warn(
                    "Analytics {} request failed with downstream status {}",
                    operation,
                    downstreamStatus);
            if (notFoundMeansNoData && downstreamStatus == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, NO_DATA_ERROR, exception);
            }
            if (downstreamStatus == HttpStatus.GATEWAY_TIMEOUT.value()) {
                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        publicMessage,
                        exception);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, publicMessage, exception);
        } catch (ResourceAccessException exception) {
            LOGGER.warn(
                    "Analytics {} request could not reach API Gateway ({})",
                    operation,
                    exception.getClass().getSimpleName());
            HttpStatus status = causedByTimeout(exception)
                    ? HttpStatus.GATEWAY_TIMEOUT
                    : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, publicMessage, exception);
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "Analytics {} response could not be read ({})",
                    operation,
                    exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, publicMessage, exception);
        }
    }

    private static RestClient configuredRestClient(
            AwsCredentialsProvider credentialsProvider,
            Region region) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new AwsIamRequestSigningInterceptor(
                        credentialsProvider,
                        region))
                .build();
    }

    private static URI configuredUri(String configuredValue) {
        if (!StringUtils.hasText(configuredValue)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    CONFIGURATION_ERROR);
        }

        try {
            URI uri = new URI(configuredValue.trim());
            if (!uri.isAbsolute()
                    || !("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))
                    || !StringUtils.hasText(uri.getHost())) {
                throw new URISyntaxException(configuredValue, "A full HTTP URL is required");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    CONFIGURATION_ERROR,
                    exception);
        }
    }

    private static boolean isValid(AnalyticsSummaryDto response) {
        if (response == null
                || response.totalPosts() < 0
                || !isValidAverage(response.averageWeatherAccuracy())
                || !isValidDistribution(response.ratingDistribution())
                || !isValidLocations(response.topLocations())
                || !isInstant(response.generatedAt())) {
            return false;
        }

        if (response.totalPosts() == 0) {
            return response.averageWeatherAccuracy() == null
                    && response.ratingDistribution().stream().allMatch(entry -> entry.count() == 0)
                    && response.topLocations().isEmpty();
        }
        return response.averageWeatherAccuracy() != null;
    }

    private static boolean isValidAverage(Double average) {
        return average == null
                || (Double.isFinite(average) && average >= 1 && average <= 5);
    }

    private static boolean isValidDistribution(List<RatingDistributionDto> distribution) {
        if (distribution == null || distribution.size() != 5) {
            return false;
        }
        for (int index = 0; index < distribution.size(); index++) {
            RatingDistributionDto entry = distribution.get(index);
            if (entry == null || entry.rating() != index + 1 || entry.count() < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidLocations(List<LocationPostCountDto> locations) {
        if (locations == null || locations.size() > 5) {
            return false;
        }
        long previousCount = Long.MAX_VALUE;
        for (LocationPostCountDto location : locations) {
            if (location == null
                    || !StringUtils.hasText(location.locationName())
                    || location.postCount() < 0
                    || location.postCount() > previousCount) {
                return false;
            }
            previousCount = location.postCount();
        }
        return true;
    }

    private static boolean isInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            Instant.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static boolean causedByTimeout(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static ResponseStatusException invalidResponse(String publicMessage) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, publicMessage);
    }
}
