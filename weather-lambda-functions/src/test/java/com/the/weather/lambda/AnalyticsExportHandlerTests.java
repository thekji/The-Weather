package com.the.weather.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.Crawler;
import software.amazon.awssdk.services.glue.model.CrawlerRunningException;
import software.amazon.awssdk.services.glue.model.CrawlerState;
import software.amazon.awssdk.services.glue.model.GetCrawlerRequest;
import software.amazon.awssdk.services.glue.model.GetCrawlerResponse;
import software.amazon.awssdk.services.glue.model.LastCrawlInfo;
import software.amazon.awssdk.services.glue.model.LastCrawlStatus;
import software.amazon.awssdk.services.glue.model.StartCrawlerRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class AnalyticsExportHandlerTests {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AnalyticsExportHandler.Config CONFIG =
            new AnalyticsExportHandler.Config(
                    "CommunityPosts",
                    "analytics-bucket",
                    "community-analytics/posts/posts.json",
                    "posts-crawler",
                    Region.AP_SOUTHEAST_2);

    private DynamoDbClient dynamoDb;
    private S3Client s3;
    private GlueClient glue;
    private AnalyticsExportHandler handler;

    @BeforeEach
    void setUp() {
        dynamoDb = mock(DynamoDbClient.class);
        s3 = mock(S3Client.class);
        glue = mock(GlueClient.class);
        handler = new AnalyticsExportHandler(dynamoDb, s3, glue, JSON, CONFIG);
        crawler(CrawlerState.READY, LastCrawlStatus.SUCCEEDED);
    }

    @Test
    void refreshPaginatesAndUploadsExactNdjsonBeforeStartingCrawler() throws Exception {
        Map<String, AttributeValue> nextKey = Map.of("postID", string("post_1"));
        Map<String, AttributeValue> first = post("post_1", "RMIT \"Saigon\"\nCampus", true);
        Map<String, AttributeValue> second = post("post_2", "Landmark 81", false);
        when(dynamoDb.scan(any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder().items(first).lastEvaluatedKey(nextKey).build())
                .thenReturn(ScanResponse.builder().items(second).lastEvaluatedKey(Map.of()).build());

        APIGatewayV2HTTPResponse response = handler.handleRequest(event("POST"), null);

        assertEquals(202, response.getStatusCode());
        assertEquals("REFRESHING", body(response).path("status").asText());

        ArgumentCaptor<ScanRequest> scanRequests = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDb, org.mockito.Mockito.times(2)).scan(scanRequests.capture());
        assertTrue(scanRequests.getAllValues().getFirst().exclusiveStartKey().isEmpty());
        assertEquals(nextKey, scanRequests.getAllValues().get(1).exclusiveStartKey());
        assertEquals(Set.of(
                        "postID", "userID", "locationID", "locationName", "createdAt",
                        "weatherAccuracyRating", "helpfulCount", "notHelpfulCount"),
                Set.copyOf(scanRequests.getValue().expressionAttributeNames().values()));

        ArgumentCaptor<PutObjectRequest> putRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> requestBody = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(putRequest.capture(), requestBody.capture());
        assertEquals("analytics-bucket", putRequest.getValue().bucket());
        assertEquals("community-analytics/posts/posts.json", putRequest.getValue().key());
        assertEquals("application/x-ndjson", putRequest.getValue().contentType());

        String ndjson = requestBody(requestBody.getValue());
        assertTrue(ndjson.endsWith("\n"));
        String[] lines = ndjson.stripTrailing().split("\n");
        assertEquals(2, lines.length, "escaped newlines must not create extra NDJSON records");
        JsonNode firstLine = JSON.readTree(lines[0]);
        JsonNode secondLine = JSON.readTree(lines[1]);
        assertEquals("RMIT \"Saigon\"\nCampus", firstLine.path("locationName").asText());
        assertEquals(List.of(
                        "postID", "userID", "locationID", "locationName", "createdAt",
                        "weatherAccuracyRating", "helpfulCount", "notHelpfulCount"),
                fieldNames(firstLine));
        assertEquals(7, firstLine.path("helpfulCount").asInt());
        assertEquals(0, secondLine.path("helpfulCount").asInt());
        assertEquals(0, secondLine.path("notHelpfulCount").asInt());

        InOrder order = inOrder(dynamoDb, s3, glue);
        order.verify(glue).getCrawler(any(GetCrawlerRequest.class));
        order.verify(dynamoDb, org.mockito.Mockito.times(2)).scan(any(ScanRequest.class));
        order.verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        order.verify(glue).startCrawler(any(StartCrawlerRequest.class));
    }

    @Test
    void refreshUploadsAnEmptyObjectForAnEmptyTable() throws Exception {
        when(dynamoDb.scan(any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder().items(List.of()).lastEvaluatedKey(Map.of()).build());

        APIGatewayV2HTTPResponse response = handler.handleRequest(event("POST"), null);

        assertEquals(202, response.getStatusCode());
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(any(PutObjectRequest.class), body.capture());
        assertEquals("", requestBody(body.getValue()));
        verify(glue).startCrawler(any(StartCrawlerRequest.class));
    }

    @Test
    void missingRequiredFieldFailsWithoutUploadingOrStartingCrawler() throws Exception {
        Map<String, AttributeValue> invalid = new LinkedHashMap<>(post("post_1", "RMIT", true));
        invalid.remove("locationID");
        when(dynamoDb.scan(any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder().items(invalid).lastEvaluatedKey(Map.of()).build());

        APIGatewayV2HTTPResponse response = handler.handleRequest(event("POST"), null);

        assertEquals(502, response.getStatusCode());
        assertEquals("Unable to refresh analytics data.", body(response).path("message").asText());
        verifyNoInteractions(s3);
        verify(glue, never()).startCrawler(any(StartCrawlerRequest.class));
    }

    @Test
    void invalidRatingFailsSafely() throws Exception {
        Map<String, AttributeValue> invalid = new LinkedHashMap<>(post("post_1", "RMIT", true));
        invalid.put("weatherAccuracyRating", number("6"));
        when(dynamoDb.scan(any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder().items(invalid).lastEvaluatedKey(Map.of()).build());

        APIGatewayV2HTTPResponse response = handler.handleRequest(event("POST"), null);

        assertEquals(502, response.getStatusCode());
        verifyNoInteractions(s3);
    }

    @Test
    void runningCrawlerReturnsRefreshingWithoutStartingAnotherExport() throws Exception {
        crawler(CrawlerState.RUNNING, LastCrawlStatus.SUCCEEDED);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event("POST"), null);

        assertEquals(202, response.getStatusCode());
        assertEquals("REFRESHING", body(response).path("status").asText());
        assertEquals(AnalyticsExportHandler.ALREADY_REFRESHING_MESSAGE,
                body(response).path("message").asText());
        verifyNoInteractions(dynamoDb, s3);
        verify(glue, never()).startCrawler(any(StartCrawlerRequest.class));
    }

    @Test
    void crawlerStartRaceStillReturnsRefreshing() throws Exception {
        when(dynamoDb.scan(any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder().items(List.of()).lastEvaluatedKey(Map.of()).build());
        when(glue.startCrawler(any(StartCrawlerRequest.class)))
                .thenThrow(CrawlerRunningException.builder().message("already running").build());

        APIGatewayV2HTTPResponse response = handler.handleRequest(event("POST"), null);

        assertEquals(202, response.getStatusCode());
        assertEquals(AnalyticsExportHandler.ALREADY_REFRESHING_MESSAGE,
                body(response).path("message").asText());
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void mapsCrawlerStateAndLastRunIntoPublicStatus() throws Exception {
        crawler(CrawlerState.RUNNING, LastCrawlStatus.SUCCEEDED);
        assertEquals("REFRESHING", body(handler.handleRequest(event("GET"), null))
                .path("status").asText());

        crawler(CrawlerState.STOPPING, LastCrawlStatus.SUCCEEDED);
        assertEquals("REFRESHING", body(handler.handleRequest(event("GET"), null))
                .path("status").asText());

        crawler(CrawlerState.READY, LastCrawlStatus.SUCCEEDED);
        assertEquals("READY", body(handler.handleRequest(event("GET"), null))
                .path("status").asText());

        crawler(CrawlerState.READY, LastCrawlStatus.FAILED);
        assertEquals("FAILED", body(handler.handleRequest(event("GET"), null))
                .path("status").asText());

        when(glue.getCrawler(any(GetCrawlerRequest.class))).thenReturn(
                GetCrawlerResponse.builder().crawler(Crawler.builder()
                        .state(CrawlerState.READY).build()).build());
        assertEquals("FAILED", body(handler.handleRequest(event("GET"), null))
                .path("status").asText());
    }

    @Test
    void sdkFailuresAndTimeoutsReturnSanitizedErrors() throws Exception {
        when(glue.getCrawler(any(GetCrawlerRequest.class)))
                .thenThrow(new RuntimeException("account secret"));
        APIGatewayV2HTTPResponse unexpected = handler.handleRequest(event("GET"), null);
        assertEquals(500, unexpected.getStatusCode());
        assertFalse(unexpected.getBody().contains("account secret"));

        when(glue.getCrawler(any(GetCrawlerRequest.class)))
                .thenThrow(ApiCallTimeoutException.builder().message("internal endpoint").build());
        APIGatewayV2HTTPResponse timeout = handler.handleRequest(event("GET"), null);
        assertEquals(504, timeout.getStatusCode());
        assertFalse(timeout.getBody().contains("internal endpoint"));
    }

    @Test
    void refusesWorkWhenLambdaCannotPreserveResponseTime() throws Exception {
        Context context = mock(Context.class);
        when(context.getRemainingTimeInMillis())
                .thenReturn((int) AnalyticsExportHandler.RESPONSE_RESERVE_MILLIS);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event("POST"), context);

        assertEquals(504, response.getStatusCode());
        assertEquals("Analytics refresh request timed out.",
                body(response).path("message").asText());
        verifyNoInteractions(dynamoDb, s3, glue);
    }

    @Test
    void stopsPaginatedExportAtGatewaySafeDeadlineBeforeUpload() throws Exception {
        Map<String, AttributeValue> nextKey = Map.of("postID", string("post_2"));
        when(dynamoDb.scan(any(ScanRequest.class)))
                .thenReturn(ScanResponse.builder().items(List.of()).lastEvaluatedKey(nextKey).build());
        AtomicInteger clockReads = new AtomicInteger();
        long expired = Duration.ofMillis(
                AnalyticsExportHandler.REQUEST_WORK_BUDGET_MILLIS + 1).toNanos();
        handler = new AnalyticsExportHandler(
                dynamoDb,
                s3,
                glue,
                JSON,
                CONFIG,
                () -> clockReads.incrementAndGet() < 7 ? 0L : expired);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event("POST"), null);

        assertEquals(504, response.getStatusCode());
        verify(dynamoDb).scan(any(ScanRequest.class));
        verifyNoInteractions(s3);
        verify(glue, never()).startCrawler(any(StartCrawlerRequest.class));
    }

    @Test
    void productionAwsClientsUseBoundedCallAndAttemptTimeouts() {
        ClientOverrideConfiguration timeouts = AnalyticsExportHandler.sdkTimeoutConfiguration();

        assertEquals(Duration.ofSeconds(5), timeouts.apiCallTimeout().orElseThrow());
        assertEquals(Duration.ofSeconds(4), timeouts.apiCallAttemptTimeout().orElseThrow());
        assertTrue(timeouts.apiCallAttemptTimeout().orElseThrow()
                .compareTo(timeouts.apiCallTimeout().orElseThrow()) <= 0);
    }

    @Test
    void rejectsUnsupportedOrMissingHttpMethods() throws Exception {
        assertEquals(405, handler.handleRequest(event("DELETE"), null).getStatusCode());
        assertEquals(400, handler.handleRequest(new APIGatewayV2HTTPEvent(), null).getStatusCode());
    }

    @Test
    void validatesRequiredEnvironmentConfiguration() {
        Map<String, String> valid = Map.of(
                "COMMUNITY_POSTS_TABLE", "CommunityPosts",
                "ANALYTICS_BUCKET", "analytics-bucket",
                "ANALYTICS_KEY", "community-analytics/posts/posts.json",
                "GLUE_CRAWLER_NAME", "posts-crawler",
                "AWS_REGION", "ap-southeast-2");
        assertEquals("CommunityPosts",
                AnalyticsExportHandler.Config.fromEnvironment(valid).tableName());

        Map<String, String> missing = new LinkedHashMap<>(valid);
        missing.remove("ANALYTICS_BUCKET");
        assertThrows(IllegalArgumentException.class,
                () -> AnalyticsExportHandler.Config.fromEnvironment(missing));
    }

    private void crawler(CrawlerState state, LastCrawlStatus lastStatus) {
        when(glue.getCrawler(any(GetCrawlerRequest.class))).thenReturn(
                GetCrawlerResponse.builder().crawler(Crawler.builder()
                        .state(state)
                        .lastCrawl(LastCrawlInfo.builder().status(lastStatus).build())
                        .build()).build());
    }

    private static Map<String, AttributeValue> post(
            String postId,
            String locationName,
            boolean includeCounters) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put("postID", string(postId));
        item.put("userID", string("user_1"));
        item.put("locationID", string("location_1"));
        item.put("locationName", string(locationName));
        item.put("createdAt", string("2026-09-15T08:30:00Z"));
        item.put("weatherAccuracyRating", number("4"));
        if (includeCounters) {
            item.put("helpfulCount", number("7"));
            item.put("notHelpfulCount", number("2"));
        }
        return item;
    }

    private static AttributeValue string(String value) {
        return AttributeValue.fromS(value);
    }

    private static AttributeValue number(String value) {
        return AttributeValue.fromN(value);
    }

    private static APIGatewayV2HTTPEvent event(String method) {
        return APIGatewayV2HTTPEvent.builder()
                .withRouteKey(method + " /analytics/refresh")
                .build();
    }

    private static JsonNode body(APIGatewayV2HTTPResponse response) throws Exception {
        return JSON.readTree(response.getBody());
    }

    private static String requestBody(RequestBody body) throws IOException {
        try (var stream = body.contentStreamProvider().newStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
