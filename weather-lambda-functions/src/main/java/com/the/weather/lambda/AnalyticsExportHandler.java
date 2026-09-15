package com.the.weather.lambda;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkException;
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
import software.amazon.awssdk.services.glue.model.LastCrawlStatus;
import software.amazon.awssdk.services.glue.model.StartCrawlerRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Exports community posts as NDJSON and exposes the Glue crawler refresh status.
 * The same handler is deployed behind POST /analytics/refresh and
 * GET /analytics/refresh/status.
 */
public class AnalyticsExportHandler implements
        RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final String CONTENT_TYPE = "application/x-ndjson";
    static final String REFRESH_MESSAGE =
            "Latest community data exported and analytics catalogue refresh started.";
    static final String ALREADY_REFRESHING_MESSAGE = "Analytics refresh is already in progress.";
    static final long REQUEST_WORK_BUDGET_MILLIS = 23_000;
    static final long RESPONSE_RESERVE_MILLIS = 5_000;
    static final Duration SDK_API_CALL_TIMEOUT = Duration.ofSeconds(5);
    static final Duration SDK_API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(4);

    private static final List<String> EXPORT_FIELDS = List.of(
            "postID",
            "userID",
            "locationID",
            "locationName",
            "createdAt",
            "weatherAccuracyRating",
            "helpfulCount",
            "notHelpfulCount");

    private final DynamoDbClient dynamoDb;
    private final S3Client s3;
    private final GlueClient glue;
    private final ObjectMapper json;
    private final Config config;
    private final LongSupplier nanoTime;

    /** Required public no-argument constructor for AWS Lambda. */
    public AnalyticsExportHandler() {
        this(Config.fromEnvironment(System.getenv()));
    }

    private AnalyticsExportHandler(Config config) {
        this(
                DynamoDbClient.builder()
                        .region(config.region())
                        .overrideConfiguration(sdkTimeoutConfiguration())
                        .build(),
                S3Client.builder()
                        .region(config.region())
                        .overrideConfiguration(sdkTimeoutConfiguration())
                        .build(),
                GlueClient.builder()
                        .region(config.region())
                        .overrideConfiguration(sdkTimeoutConfiguration())
                        .build(),
                new ObjectMapper(),
                config,
                System::nanoTime);
    }

    AnalyticsExportHandler(
            DynamoDbClient dynamoDb,
            S3Client s3,
            GlueClient glue,
            ObjectMapper json,
            Config config) {
        this(dynamoDb, s3, glue, json, config, System::nanoTime);
    }

    AnalyticsExportHandler(
            DynamoDbClient dynamoDb,
            S3Client s3,
            GlueClient glue,
            ObjectMapper json,
            Config config,
            LongSupplier nanoTime) {
        this.dynamoDb = Objects.requireNonNull(dynamoDb);
        this.s3 = Objects.requireNonNull(s3);
        this.glue = Objects.requireNonNull(glue);
        this.json = Objects.requireNonNull(json);
        this.config = Objects.requireNonNull(config);
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String method = httpMethod(event);
        if (method == null) {
            return AnalyticsApiResponses.error(json, 400, "HTTP method is required.");
        }

        try {
            RequestDeadline deadline = RequestDeadline.start(context, nanoTime);
            deadline.check();
            return switch (method) {
                case "POST" -> refresh(deadline);
                case "GET" -> status(deadline);
                default -> AnalyticsApiResponses.error(json, 405, "HTTP method is not supported.");
            };
        } catch (AnalyticsExportTimeoutException | ApiCallTimeoutException
                | ApiCallAttemptTimeoutException exception) {
            log(context, exception);
            return AnalyticsApiResponses.error(json, 504, "Analytics refresh request timed out.");
        } catch (InvalidAnalyticsDataException exception) {
            log(context, exception);
            return AnalyticsApiResponses.error(json, 502, "Unable to refresh analytics data.");
        } catch (SdkException exception) {
            log(context, exception);
            return AnalyticsApiResponses.error(json, 502, "Unable to refresh analytics data.");
        } catch (RuntimeException exception) {
            log(context, exception);
            return AnalyticsApiResponses.error(json, 500, "Unable to process analytics refresh.");
        }
    }

    private APIGatewayV2HTTPResponse refresh(RequestDeadline deadline) {
        Crawler crawler = getCrawler(deadline);
        if (isRefreshing(crawler)) {
            return AnalyticsApiResponses.json(json, 202,
                    Map.of("status", "REFRESHING", "message", ALREADY_REFRESHING_MESSAGE));
        }

        byte[] dataset = exportDataset(deadline);
        deadline.check();
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(config.analyticsBucket())
                        .key(config.analyticsKey())
                        .contentType(CONTENT_TYPE)
                        .build(),
                RequestBody.fromBytes(dataset));
        deadline.check();

        try {
            deadline.check();
            glue.startCrawler(StartCrawlerRequest.builder().name(config.crawlerName()).build());
            deadline.check();
        } catch (CrawlerRunningException ignored) {
            return AnalyticsApiResponses.json(json, 202,
                    Map.of("status", "REFRESHING", "message", ALREADY_REFRESHING_MESSAGE));
        }

        return AnalyticsApiResponses.json(json, 202,
                Map.of("status", "REFRESHING", "message", REFRESH_MESSAGE));
    }

    private APIGatewayV2HTTPResponse status(RequestDeadline deadline) {
        Crawler crawler = getCrawler(deadline);
        RefreshStatus status;
        if (isRefreshing(crawler)) {
            status = RefreshStatus.REFRESHING;
        } else if (crawler != null
                && crawler.state() == CrawlerState.READY
                && crawler.lastCrawl() != null
                && crawler.lastCrawl().status() == LastCrawlStatus.SUCCEEDED) {
            status = RefreshStatus.READY;
        } else {
            status = RefreshStatus.FAILED;
        }
        return AnalyticsApiResponses.json(json, 200, Map.of("status", status.name()));
    }

    private Crawler getCrawler(RequestDeadline deadline) {
        deadline.check();
        Crawler crawler = glue.getCrawler(
                GetCrawlerRequest.builder().name(config.crawlerName()).build()).crawler();
        deadline.check();
        return crawler;
    }

    private static boolean isRefreshing(Crawler crawler) {
        if (crawler == null) return false;
        return crawler.state() == CrawlerState.RUNNING || crawler.state() == CrawlerState.STOPPING;
    }

    private byte[] exportDataset(RequestDeadline deadline) {
        StringBuilder ndjson = new StringBuilder();
        Map<String, AttributeValue> startKey = Map.of();

        do {
            ScanRequest.Builder request = ScanRequest.builder()
                    .tableName(config.tableName())
                    .projectionExpression(projectionExpression())
                    .expressionAttributeNames(projectionNames());
            if (!startKey.isEmpty()) request.exclusiveStartKey(startKey);

            deadline.check();
            ScanResponse page = dynamoDb.scan(request.build());
            deadline.check();
            for (Map<String, AttributeValue> item : page.items()) {
                try {
                    ndjson.append(json.writeValueAsString(toExportRecord(item))).append('\n');
                } catch (JsonProcessingException exception) {
                    throw new InvalidAnalyticsDataException("Could not serialize a post", exception);
                }
                deadline.check();
            }
            startKey = page.lastEvaluatedKey();
            if (startKey == null) startKey = Map.of();
        } while (!startKey.isEmpty());

        return ndjson.toString().getBytes(StandardCharsets.UTF_8);
    }

    static ClientOverrideConfiguration sdkTimeoutConfiguration() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(SDK_API_CALL_TIMEOUT)
                .apiCallAttemptTimeout(SDK_API_CALL_ATTEMPT_TIMEOUT)
                .build();
    }

    private static Map<String, Object> toExportRecord(Map<String, AttributeValue> item) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("postID", requiredString(item, "postID"));
        record.put("userID", requiredString(item, "userID"));
        record.put("locationID", requiredString(item, "locationID"));
        record.put("locationName", requiredString(item, "locationName"));
        record.put("createdAt", requiredString(item, "createdAt"));

        int rating = requiredInteger(item, "weatherAccuracyRating");
        if (rating < 1 || rating > 5) {
            throw new InvalidAnalyticsDataException("weatherAccuracyRating must be between 1 and 5");
        }
        record.put("weatherAccuracyRating", rating);
        record.put("helpfulCount", optionalCounter(item, "helpfulCount"));
        record.put("notHelpfulCount", optionalCounter(item, "notHelpfulCount"));
        return record;
    }

    private static String requiredString(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.s() == null || value.s().isBlank()) {
            throw new InvalidAnalyticsDataException("Missing required field " + name);
        }
        return value.s();
    }

    private static int requiredInteger(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null || value.n().isBlank()) {
            throw new InvalidAnalyticsDataException("Missing required field " + name);
        }
        try {
            return new BigDecimal(value.n()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new InvalidAnalyticsDataException("Invalid numeric field " + name, exception);
        }
    }

    private static int optionalCounter(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        if (value == null || value.n() == null || value.n().isBlank()) return 0;
        int count = requiredInteger(item, name);
        if (count < 0) throw new InvalidAnalyticsDataException(name + " cannot be negative");
        return count;
    }

    private static String projectionExpression() {
        return EXPORT_FIELDS.stream().map(name -> "#" + name).reduce((a, b) -> a + ", " + b)
                .orElseThrow();
    }

    private static Map<String, String> projectionNames() {
        Map<String, String> names = new LinkedHashMap<>();
        EXPORT_FIELDS.forEach(name -> names.put("#" + name, name));
        return names;
    }

    private static String httpMethod(APIGatewayV2HTTPEvent event) {
        if (event == null) return null;
        if (event.getRequestContext() != null && event.getRequestContext().getHttp() != null) {
            String method = event.getRequestContext().getHttp().getMethod();
            if (method != null && !method.isBlank()) return method.toUpperCase();
        }
        String routeKey = event.getRouteKey();
        if (routeKey == null || routeKey.isBlank()) return null;
        int separator = routeKey.indexOf(' ');
        return (separator < 0 ? routeKey : routeKey.substring(0, separator)).toUpperCase();
    }

    private static void log(Context context, Exception exception) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(exception.getClass().getSimpleName() + ": "
                    + String.valueOf(exception.getMessage()) + System.lineSeparator());
        }
    }

    enum RefreshStatus {
        REFRESHING,
        READY,
        FAILED
    }

    /**
     * Monotonic deadline that leaves time to serialize a controlled response before
     * the Lambda timeout and API Gateway's 30-second integration ceiling.
     */
    static final class RequestDeadline {
        private final LongSupplier nanoTime;
        private final long deadlineNanos;

        private RequestDeadline(LongSupplier nanoTime, long deadlineNanos) {
            this.nanoTime = nanoTime;
            this.deadlineNanos = deadlineNanos;
        }

        static RequestDeadline start(Context context, LongSupplier nanoTime) {
            long now = nanoTime.getAsLong();
            long lambdaBudgetMillis = Long.MAX_VALUE;
            if (context != null) {
                lambdaBudgetMillis = Math.max(0L,
                        (long) context.getRemainingTimeInMillis() - RESPONSE_RESERVE_MILLIS);
            }
            long workBudgetMillis = Math.min(REQUEST_WORK_BUDGET_MILLIS, lambdaBudgetMillis);
            long deadlineNanos = now + Duration.ofMillis(workBudgetMillis).toNanos();
            return new RequestDeadline(nanoTime, deadlineNanos);
        }

        void check() {
            if (deadlineNanos - nanoTime.getAsLong() <= 0) {
                throw new AnalyticsExportTimeoutException();
            }
        }
    }

    record Config(
            String tableName,
            String analyticsBucket,
            String analyticsKey,
            String crawlerName,
            Region region) {

        Config {
            tableName = requireValue(tableName, "COMMUNITY_POSTS_TABLE");
            analyticsBucket = requireValue(analyticsBucket, "ANALYTICS_BUCKET");
            analyticsKey = requireValue(analyticsKey, "ANALYTICS_KEY");
            crawlerName = requireValue(crawlerName, "GLUE_CRAWLER_NAME");
            Objects.requireNonNull(region, "AWS_REGION is required");
        }

        static Config fromEnvironment(Map<String, String> environment) {
            String rawRegion = requireValue(environment.get("AWS_REGION"), "AWS_REGION");
            return new Config(
                    environment.get("COMMUNITY_POSTS_TABLE"),
                    environment.get("ANALYTICS_BUCKET"),
                    environment.get("ANALYTICS_KEY"),
                    environment.get("GLUE_CRAWLER_NAME"),
                    Region.of(rawRegion));
        }
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    static final class InvalidAnalyticsDataException extends RuntimeException {
        InvalidAnalyticsDataException(String message) {
            super(message);
        }

        InvalidAnalyticsDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class AnalyticsExportTimeoutException extends RuntimeException {
        AnalyticsExportTimeoutException() {
            super("Analytics export exceeded its request deadline");
        }
    }
}
