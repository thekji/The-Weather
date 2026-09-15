package com.the.weather.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.AthenaException;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionResponse;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GetTableRequest;
import software.amazon.awssdk.services.glue.model.GetTableResponse;
import software.amazon.awssdk.services.glue.model.Table;

class AnalyticsQueryHandlerTests {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-15T08:45:00Z"), ZoneOffset.UTC);
    private static final AnalyticsQueryHandler.Config CONFIG = new AnalyticsQueryHandler.Config(
            "theweather_analytics",
            "community_posts",
            "weatheraccuracyrating",
            "locationname",
            "s3://analytics-bucket/athena-results/",
            "primary",
            Region.AP_SOUTHEAST_2);

    private AthenaClient athena;
    private GlueClient glue;

    @BeforeEach
    void setUp() {
        athena = mock(AthenaClient.class);
        glue = mock(GlueClient.class);
        when(glue.getTable(any(GetTableRequest.class))).thenReturn(GetTableResponse.builder()
                .table(Table.builder().name("community_posts").build()).build());
        successfulStarts();
        when(athena.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(execution(QueryExecutionState.SUCCEEDED));
        successfulResults();
    }

    @Test
    void queriesAllFourMetricsAndReturnsNormalizedSummary() throws Exception {
        AnalyticsQueryHandler handler = handler(50, milliseconds -> {});

        APIGatewayV2HTTPResponse response = handler.handleRequest(null, null);

        assertEquals(200, response.getStatusCode());
        JsonNode body = body(response);
        assertEquals(76, body.path("totalPosts").asLong());
        assertEquals(4.12, body.path("averageWeatherAccuracy").asDouble());
        assertEquals("2026-09-15T08:45:00Z", body.path("generatedAt").asText());
        assertEquals(5, body.path("ratingDistribution").size());
        assertEquals(1, body.path("ratingDistribution").get(0).path("rating").asInt());
        assertEquals(3, body.path("ratingDistribution").get(0).path("count").asLong());
        assertEquals(0, body.path("ratingDistribution").get(1).path("count").asLong());
        assertEquals(12, body.path("ratingDistribution").get(2).path("count").asLong());
        assertEquals(31, body.path("ratingDistribution").get(4).path("count").asLong());
        assertEquals("Petrolimex", body.path("topLocations").get(0).path("locationName").asText());
        assertEquals(12, body.path("topLocations").get(0).path("postCount").asLong());

        ArgumentCaptor<GetTableRequest> table = ArgumentCaptor.forClass(GetTableRequest.class);
        verify(glue).getTable(table.capture());
        assertEquals("theweather_analytics", table.getValue().databaseName());
        assertEquals("community_posts", table.getValue().name());

        ArgumentCaptor<StartQueryExecutionRequest> starts =
                ArgumentCaptor.forClass(StartQueryExecutionRequest.class);
        verify(athena, org.mockito.Mockito.times(4)).startQueryExecution(starts.capture());
        assertTrue(starts.getAllValues().stream().allMatch(request ->
                request.queryExecutionContext().catalog().equals("AwsDataCatalog")
                        && request.queryExecutionContext().database().equals("theweather_analytics")
                        && request.resultConfiguration().outputLocation()
                                .equals("s3://analytics-bucket/athena-results/")
                        && request.workGroup().equals("primary")));
        assertTrue(starts.getAllValues().stream().map(StartQueryExecutionRequest::queryString)
                .anyMatch(sql -> sql.equals(
                        "SELECT COUNT(*) AS total_posts FROM \"theweather_analytics\".\"community_posts\";")));
        assertTrue(starts.getAllValues().stream().map(StartQueryExecutionRequest::queryString)
                .anyMatch(sql -> sql.contains("ROUND(AVG(\"weatheraccuracyrating\"), 2)")));
        assertTrue(starts.getAllValues().stream().map(StartQueryExecutionRequest::queryString)
                .anyMatch(sql -> sql.contains("GROUP BY \"weatheraccuracyrating\"")));
        assertTrue(starts.getAllValues().stream().map(StartQueryExecutionRequest::queryString)
                .anyMatch(sql -> sql.contains(
                        "\"locationname\" AS location_name")
                        && sql.contains(
                                "GROUP BY \"locationname\" ORDER BY post_count DESC LIMIT 5")));

        InOrder order = inOrder(athena);
        order.verify(athena, org.mockito.Mockito.times(4))
                .startQueryExecution(any(StartQueryExecutionRequest.class));
        order.verify(athena, org.mockito.Mockito.times(4))
                .getQueryExecution(any(GetQueryExecutionRequest.class));
        order.verify(athena, org.mockito.Mockito.times(4))
                .getQueryResults(any(GetQueryResultsRequest.class));
    }

    @Test
    void emptyDatasetReturnsZeroNullFiveRatingsAndNoLocations() throws Exception {
        when(athena.getQueryResults(any(GetQueryResultsRequest.class))).thenAnswer(invocation -> {
            String id = invocation.<GetQueryResultsRequest>getArgument(0).queryExecutionId();
            return switch (id) {
                case "total" -> results(row("total_posts"), row("0"));
                case "average" -> results(row("average_accuracy"), nullRow());
                case "distribution" -> results(row("rating", "post_count"));
                case "locations" -> results(row("location_name", "post_count"));
                default -> throw new AssertionError(id);
            };
        });

        JsonNode body = body(handler(50, milliseconds -> {}).handleRequest(null, null));

        assertEquals(0, body.path("totalPosts").asLong());
        assertTrue(body.path("averageWeatherAccuracy").isNull());
        assertEquals(5, body.path("ratingDistribution").size());
        for (JsonNode rating : body.path("ratingDistribution")) {
            assertEquals(0, rating.path("count").asLong());
        }
        assertTrue(body.path("topLocations").isEmpty());
    }

    @Test
    void pollsQueuedQueriesWithStrictAttemptAndSleepBounds() throws Exception {
        when(athena.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(execution(QueryExecutionState.QUEUED));
        AtomicInteger sleeps = new AtomicInteger();

        APIGatewayV2HTTPResponse response = handler(3, milliseconds -> {
            assertEquals(400, milliseconds);
            sleeps.incrementAndGet();
        }).handleRequest(null, null);

        assertEquals(504, response.getStatusCode());
        assertEquals("Analytics query timed out.", body(response).path("message").asText());
        assertEquals(2, sleeps.get());
        verify(athena, org.mockito.Mockito.times(12))
                .getQueryExecution(any(GetQueryExecutionRequest.class));
        verify(athena, never()).getQueryResults(any(GetQueryResultsRequest.class));
    }

    @Test
    void sdkCallsHaveStrictCallAndAttemptTimeouts() {
        var configuration = AnalyticsQueryHandler.sdkTimeoutConfiguration();

        assertEquals(Duration.ofSeconds(5), configuration.apiCallTimeout().orElseThrow());
        assertEquals(Duration.ofSeconds(4),
                configuration.apiCallAttemptTimeout().orElseThrow());
    }

    @Test
    void returnsTimeoutBeforeAwsCallsWhenLambdaCannotPreserveResponseReserve()
            throws Exception {
        Context context = mock(Context.class);
        when(context.getRemainingTimeInMillis()).thenReturn(4_999);

        APIGatewayV2HTTPResponse response = handler(
                50, milliseconds -> {}, () -> 0L).handleRequest(null, context);

        assertEquals(504, response.getStatusCode());
        assertEquals("Analytics query timed out.", body(response).path("message").asText());
        verify(glue, never()).getTable(any(GetTableRequest.class));
        verify(athena, never()).startQueryExecution(any(StartQueryExecutionRequest.class));
    }

    @Test
    void skipsSleepThatWouldConsumeLambdaResponseReserve() throws Exception {
        when(athena.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(execution(QueryExecutionState.QUEUED));
        Context context = mock(Context.class);
        when(context.getRemainingTimeInMillis()).thenReturn(5_300);
        AtomicInteger sleeps = new AtomicInteger();

        APIGatewayV2HTTPResponse response = handler(
                50, milliseconds -> sleeps.incrementAndGet(), () -> 0L)
                .handleRequest(null, context);

        assertEquals(504, response.getStatusCode());
        assertEquals(0, sleeps.get());
        verify(athena, org.mockito.Mockito.times(4))
                .getQueryExecution(any(GetQueryExecutionRequest.class));
        verify(athena, never()).getQueryResults(any(GetQueryResultsRequest.class));
    }

    @Test
    void apiGatewayWorkBudgetStopsSlowPollingBeforeMaximumAttempts() throws Exception {
        AtomicLong nowNanos = new AtomicLong();
        when(athena.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenAnswer(invocation -> {
                    nowNanos.addAndGet(Duration.ofSeconds(2).toNanos());
                    return execution(QueryExecutionState.QUEUED);
                });
        Context context = mock(Context.class);
        when(context.getRemainingTimeInMillis()).thenReturn(60_000);

        APIGatewayV2HTTPResponse response = handler(
                50, milliseconds -> {}, nowNanos::get).handleRequest(null, context);

        assertEquals(504, response.getStatusCode());
        assertEquals("Analytics query timed out.", body(response).path("message").asText());
        verify(athena, org.mockito.Mockito.times(10))
                .getQueryExecution(any(GetQueryExecutionRequest.class));
        verify(athena, never()).getQueryResults(any(GetQueryResultsRequest.class));
    }

    @Test
    void failedAndCancelledQueriesReturnSanitizedBadGateway() throws Exception {
        when(athena.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(execution(QueryExecutionState.FAILED));
        APIGatewayV2HTTPResponse failed = handler(3, milliseconds -> {})
                .handleRequest(null, null);
        assertEquals(502, failed.getStatusCode());
        assertEquals("Unable to query analytics data.", body(failed).path("message").asText());

        when(athena.getQueryExecution(any(GetQueryExecutionRequest.class)))
                .thenReturn(execution(QueryExecutionState.CANCELLED));
        APIGatewayV2HTTPResponse cancelled = handler(3, milliseconds -> {})
                .handleRequest(null, null);
        assertEquals(502, cancelled.getStatusCode());
        assertFalse(cancelled.getBody().toLowerCase().contains("cancelled"));
    }

    @Test
    void missingGlueTableReturnsNoDataWithoutStartingAthena() throws Exception {
        when(glue.getTable(any(GetTableRequest.class))).thenThrow(
                EntityNotFoundException.builder().message("internal catalog details").build());

        APIGatewayV2HTTPResponse response = handler(3, milliseconds -> {})
                .handleRequest(null, null);

        assertEquals(404, response.getStatusCode());
        assertEquals("No analytics data yet.", body(response).path("message").asText());
        assertFalse(response.getBody().contains("internal catalog details"));
        verify(athena, never()).startQueryExecution(any(StartQueryExecutionRequest.class));
    }

    @Test
    void awsFailuresAndTimeoutsUseControlledResponses() throws Exception {
        when(athena.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenThrow(AthenaException.builder().message("secret AWS detail").build());
        APIGatewayV2HTTPResponse failure = handler(3, milliseconds -> {})
                .handleRequest(null, null);
        assertEquals(502, failure.getStatusCode());
        assertFalse(failure.getBody().contains("secret AWS detail"));

        when(athena.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenThrow(ApiCallTimeoutException.builder().message("private endpoint").build());
        APIGatewayV2HTTPResponse timeout = handler(3, milliseconds -> {})
                .handleRequest(null, null);
        assertEquals(504, timeout.getStatusCode());
        assertFalse(timeout.getBody().contains("private endpoint"));
    }

    @Test
    void malformedAthenaResultsFailSafely() throws Exception {
        when(athena.getQueryResults(any(GetQueryResultsRequest.class)))
                .thenReturn(results(row("total_posts"), row("not-a-count")));

        APIGatewayV2HTTPResponse response = handler(3, milliseconds -> {})
                .handleRequest(null, null);

        assertEquals(502, response.getStatusCode());
        assertEquals("Unable to query analytics data.", body(response).path("message").asText());
    }

    @Test
    void validatesIdentifiersAndNormalizesConfigurationDefaults() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("ATHENA_DATABASE", "theweather_analytics");
        environment.put("ATHENA_TABLE", "community_posts");
        environment.put("ATHENA_OUTPUT_LOCATION", "s3://analytics-bucket/athena-results");
        environment.put("AWS_REGION", "ap-southeast-2");

        AnalyticsQueryHandler.Config config =
                AnalyticsQueryHandler.Config.fromEnvironment(environment);
        assertEquals("weatheraccuracyrating", config.ratingColumn());
        assertEquals("locationname", config.locationColumn());
        assertEquals("primary", config.workgroup());
        assertEquals("s3://analytics-bucket/athena-results/", config.outputLocation());

        assertThrows(IllegalArgumentException.class, () -> new AnalyticsQueryHandler.Config(
                "theweather_analytics; DROP TABLE x",
                "community_posts",
                "weatheraccuracyrating",
                "locationname",
                "s3://analytics-bucket/athena-results/",
                "primary",
                Region.AP_SOUTHEAST_2));
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsQueryHandler.Config(
                "theweather_analytics",
                "community_posts",
                "weatheraccuracyrating",
                "locationname",
                "https://not-s3.example/results/",
                "primary",
                Region.AP_SOUTHEAST_2));
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsQueryHandler.Config(
                "theweather_analytics",
                "community_posts",
                "weatheraccuracyrating",
                "locationname",
                "s3://analytics-bucket/community-analytics/posts/results/",
                "primary",
                Region.AP_SOUTHEAST_2));
    }

    @Test
    void querySqlUsesConfiguredGlueColumnNames() {
        AnalyticsQueryHandler.Config config = new AnalyticsQueryHandler.Config(
                "analytics", "posts", "rating_value", "place_name",
                "s3://bucket/results/", "analytics-workgroup", Region.US_EAST_1);
        AnalyticsQueryHandler handler = new AnalyticsQueryHandler(
                athena, glue, JSON, CLOCK, milliseconds -> {}, config, 2, 400);

        List<String> sql = List.copyOf(handler.sqlQueries().values());

        assertTrue(sql.get(1).contains("AVG(\"rating_value\")"));
        assertTrue(sql.get(2).contains("GROUP BY \"rating_value\""));
        assertTrue(sql.get(3).contains("\"place_name\" AS location_name"));
        assertTrue(sql.get(3).contains("GROUP BY \"place_name\""));
    }

    private AnalyticsQueryHandler handler(
            int maxAttempts,
            AnalyticsQueryHandler.Sleeper sleeper) {
        return new AnalyticsQueryHandler(
                athena, glue, JSON, CLOCK, sleeper, CONFIG, maxAttempts, 400);
    }

    private AnalyticsQueryHandler handler(
            int maxAttempts,
            AnalyticsQueryHandler.Sleeper sleeper,
            java.util.function.LongSupplier nanoTime) {
        return new AnalyticsQueryHandler(
                athena, glue, JSON, CLOCK, sleeper, CONFIG, maxAttempts, 400, nanoTime);
    }

    private void successfulStarts() {
        when(athena.startQueryExecution(any(StartQueryExecutionRequest.class)))
                .thenReturn(started("total"), started("average"), started("distribution"),
                        started("locations"));
    }

    private void successfulResults() {
        when(athena.getQueryResults(any(GetQueryResultsRequest.class))).thenAnswer(invocation -> {
            String id = invocation.<GetQueryResultsRequest>getArgument(0).queryExecutionId();
            return switch (id) {
                case "total" -> results(row("total_posts"), row("76"));
                case "average" -> results(row("average_accuracy"), row("4.12"));
                case "distribution" -> results(
                        row("rating", "post_count"),
                        row("1", "3"),
                        row("3", "12"),
                        row("4", "25"),
                        row("5", "31"));
                case "locations" -> results(
                        row("location_name", "post_count"),
                        row("Petrolimex", "12"),
                        row("RMIT", "9"));
                default -> throw new AssertionError(id);
            };
        });
    }

    private static StartQueryExecutionResponse started(String id) {
        return StartQueryExecutionResponse.builder().queryExecutionId(id).build();
    }

    private static GetQueryExecutionResponse execution(QueryExecutionState state) {
        return GetQueryExecutionResponse.builder()
                .queryExecution(QueryExecution.builder()
                        .status(QueryExecutionStatus.builder().state(state).build())
                        .build())
                .build();
    }

    private static GetQueryResultsResponse results(Row... rows) {
        return GetQueryResultsResponse.builder()
                .resultSet(ResultSet.builder().rows(rows).build())
                .build();
    }

    private static Row row(String... values) {
        return Row.builder().data(java.util.Arrays.stream(values)
                .map(value -> Datum.builder().varCharValue(value).build())
                .toList()).build();
    }

    private static Row nullRow() {
        return Row.builder().data(Datum.builder().build()).build();
    }

    private static JsonNode body(APIGatewayV2HTTPResponse response) throws Exception {
        return JSON.readTree(response.getBody());
    }
}
