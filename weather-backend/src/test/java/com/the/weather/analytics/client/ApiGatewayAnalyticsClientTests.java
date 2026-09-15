package com.the.weather.analytics.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.the.weather.analytics.dto.AnalyticsRefreshResponseDto;
import com.the.weather.analytics.dto.AnalyticsRefreshStatus;
import com.the.weather.analytics.dto.AnalyticsRefreshStatusDto;
import com.the.weather.analytics.dto.AnalyticsSummaryDto;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class ApiGatewayAnalyticsClientTests {

    private static final String REFRESH_URL = "https://gateway.test/analytics/refresh";
    private static final String STATUS_URL = "https://gateway.test/analytics/refresh/status";
    private static final String SUMMARY_URL = "https://gateway.test/analytics/summary";

    @Test
    void refreshPostsToTheConfiguredGatewayRouteAndMapsTheResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayAnalyticsClient client = client(builder);

        server.expect(once(), requestTo(REFRESH_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "status": "REFRESHING",
                          "message": "Latest community data exported and analytics catalogue refresh started."
                        }
                        """, MediaType.APPLICATION_JSON));

        AnalyticsRefreshResponseDto result = client.refresh();

        assertThat(result.status()).isEqualTo(AnalyticsRefreshStatus.REFRESHING);
        assertThat(result.message()).contains("analytics catalogue refresh started");
        server.verify();
    }

    @Test
    void statusGetsTheConfiguredGatewayRouteAndMapsEveryPublicState() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayAnalyticsClient client = client(builder);

        server.expect(once(), requestTo(STATUS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"READY\"}", MediaType.APPLICATION_JSON));

        AnalyticsRefreshStatusDto result = client.refreshStatus();

        assertThat(result.status()).isEqualTo(AnalyticsRefreshStatus.READY);
        server.verify();
    }

    @Test
    void summaryMapsTheAthenaResultWithoutCalculatingMetricsLocally() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayAnalyticsClient client = client(builder);

        server.expect(once(), requestTo(SUMMARY_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(populatedSummary(), MediaType.APPLICATION_JSON));

        AnalyticsSummaryDto result = client.summary();

        assertThat(result.totalPosts()).isEqualTo(76);
        assertThat(result.averageWeatherAccuracy()).isEqualTo(4.12);
        assertThat(result.ratingDistribution())
                .extracting(entry -> entry.count())
                .containsExactly(3L, 5L, 12L, 25L, 31L);
        assertThat(result.topLocations())
                .extracting(location -> location.locationName())
                .containsExactly("Petrolimex", "RMIT");
        assertThat(result.generatedAt()).isEqualTo("2026-09-15T08:45:00Z");
        server.verify();
    }

    @Test
    void summaryAcceptsTheDefinedEmptyDatasetShapeAndNullableAverage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayAnalyticsClient client = client(builder);

        server.expect(once(), requestTo(SUMMARY_URL))
                .andRespond(withSuccess("""
                        {
                          "totalPosts": 0,
                          "averageWeatherAccuracy": null,
                          "ratingDistribution": [
                            {"rating": 1, "count": 0},
                            {"rating": 2, "count": 0},
                            {"rating": 3, "count": 0},
                            {"rating": 4, "count": 0},
                            {"rating": 5, "count": 0}
                          ],
                          "topLocations": [],
                          "generatedAt": "2026-09-15T08:45:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        AnalyticsSummaryDto result = client.summary();

        assertThat(result.totalPosts()).isZero();
        assertThat(result.averageWeatherAccuracy()).isNull();
        assertThat(result.ratingDistribution()).allMatch(entry -> entry.count() == 0);
        assertThat(result.topLocations()).isEmpty();
        server.verify();
    }

    @Test
    void summaryMapsAMissingGlueTableToTheControlledNoDataResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayAnalyticsClient client = client(builder);

        server.expect(once(), requestTo(SUMMARY_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"raw downstream detail\"}"));

        assertThatThrownBy(client::summary)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason())
                            .isEqualTo("No analytics data is available yet.");
                });
        server.verify();
    }

    @Test
    void downstreamAwsFailureBecomesASanitizedBadGatewayResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayAnalyticsClient client = client(builder);

        server.expect(once(), requestTo(SUMMARY_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"AccessDenied: secret internal detail\"}"));

        assertThatThrownBy(client::summary)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).isEqualTo("Could not load analytics.");
                    assertThat(exception.getReason()).doesNotContain("AccessDenied");
                });
        server.verify();
    }

    @Test
    void downstreamTimeoutPreservesAGatewayTimeoutWithoutExposingItsBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayAnalyticsClient client = client(builder);

        server.expect(once(), requestTo(STATUS_URL))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Athena query execution detail\"}"));

        assertThatThrownBy(client::refreshStatus)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(exception.getReason())
                            .isEqualTo("Could not update the analytics catalogue.");
                });
        server.verify();
    }

    @Test
    void malformedLambdaPayloadBecomesAControlledBadGatewayResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayAnalyticsClient client = client(builder);

        server.expect(once(), requestTo(SUMMARY_URL))
                .andRespond(withSuccess("""
                        {
                          "totalPosts": 1,
                          "averageWeatherAccuracy": 4,
                          "ratingDistribution": [{"rating": 4, "count": 1}],
                          "topLocations": [],
                          "generatedAt": "not-an-instant"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::summary)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).isEqualTo("Could not load analytics.");
                });
        server.verify();
    }

    @Test
    void missingOrInvalidConfigurationReturnsServiceUnavailableBeforeAnyCall() {
        RestClient restClient = RestClient.builder().build();
        ApiGatewayAnalyticsClient missing = new ApiGatewayAnalyticsClient(
                restClient,
                "",
                STATUS_URL,
                SUMMARY_URL);
        ApiGatewayAnalyticsClient invalid = new ApiGatewayAnalyticsClient(
                restClient,
                REFRESH_URL,
                "not-a-full-url",
                SUMMARY_URL);

        assertConfigurationError(missing::refresh);
        assertConfigurationError(invalid::refreshStatus);
    }

    @Test
    void productionTimeoutsAreBoundedAndLongEnoughForAthena() {
        assertThat(ApiGatewayAnalyticsClient.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(3));
        assertThat(ApiGatewayAnalyticsClient.READ_TIMEOUT).isEqualTo(Duration.ofSeconds(35));
    }

    private static ApiGatewayAnalyticsClient client(RestClient.Builder builder) {
        return new ApiGatewayAnalyticsClient(
                builder.build(),
                REFRESH_URL,
                STATUS_URL,
                SUMMARY_URL);
    }

    private static void assertConfigurationError(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode())
                            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason())
                            .isEqualTo("Analytics API Gateway is not configured.");
                });
    }

    private static String populatedSummary() {
        return """
                {
                  "totalPosts": 76,
                  "averageWeatherAccuracy": 4.12,
                  "ratingDistribution": [
                    {"rating": 1, "count": 3},
                    {"rating": 2, "count": 5},
                    {"rating": 3, "count": 12},
                    {"rating": 4, "count": 25},
                    {"rating": 5, "count": 31}
                  ],
                  "topLocations": [
                    {"locationName": "Petrolimex", "postCount": 12},
                    {"locationName": "RMIT", "postCount": 9}
                  ],
                  "generatedAt": "2026-09-15T08:45:00Z"
                }
                """;
    }
}
