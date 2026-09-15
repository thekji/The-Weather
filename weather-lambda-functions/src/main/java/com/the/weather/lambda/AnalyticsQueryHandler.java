package com.the.weather.lambda;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.model.QueryExecutionContext;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.glue.model.EntityNotFoundException;
import software.amazon.awssdk.services.glue.model.GetTableRequest;

/** Executes the four dashboard metrics in Athena and returns a normalized summary. */
public class AnalyticsQueryHandler implements
        RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    static final int DEFAULT_MAX_POLL_ATTEMPTS = 50;
    static final long DEFAULT_POLL_INTERVAL_MILLIS = 400;
    static final long REQUEST_WORK_BUDGET_MILLIS = 20_000;
    static final long RESPONSE_RESERVE_MILLIS = 5_000;
    static final Duration SDK_API_CALL_TIMEOUT = Duration.ofSeconds(5);
    static final Duration SDK_API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(4);
    private static final Pattern ATHENA_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final AthenaClient athena;
    private final GlueClient glue;
    private final ObjectMapper json;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Config config;
    private final int maxPollAttempts;
    private final long pollIntervalMillis;
    private final LongSupplier nanoTime;

    /** Required public no-argument constructor for AWS Lambda. */
    public AnalyticsQueryHandler() {
        this(Config.fromEnvironment(System.getenv()));
    }

    private AnalyticsQueryHandler(Config config) {
        this(
                AthenaClient.builder()
                        .region(config.region())
                        .overrideConfiguration(sdkTimeoutConfiguration())
                        .build(),
                GlueClient.builder()
                        .region(config.region())
                        .overrideConfiguration(sdkTimeoutConfiguration())
                        .build(),
                new ObjectMapper(),
                Clock.systemUTC(),
                Thread::sleep,
                config,
                DEFAULT_MAX_POLL_ATTEMPTS,
                DEFAULT_POLL_INTERVAL_MILLIS,
                System::nanoTime);
    }

    AnalyticsQueryHandler(
            AthenaClient athena,
            GlueClient glue,
            ObjectMapper json,
            Clock clock,
            Sleeper sleeper,
            Config config,
            int maxPollAttempts,
            long pollIntervalMillis) {
        this(athena, glue, json, clock, sleeper, config, maxPollAttempts,
                pollIntervalMillis, System::nanoTime);
    }

    AnalyticsQueryHandler(
            AthenaClient athena,
            GlueClient glue,
            ObjectMapper json,
            Clock clock,
            Sleeper sleeper,
            Config config,
            int maxPollAttempts,
            long pollIntervalMillis,
            LongSupplier nanoTime) {
        this.athena = Objects.requireNonNull(athena);
        this.glue = Objects.requireNonNull(glue);
        this.json = Objects.requireNonNull(json);
        this.clock = Objects.requireNonNull(clock);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.config = Objects.requireNonNull(config);
        if (maxPollAttempts < 1) throw new IllegalArgumentException("maxPollAttempts must be positive");
        if (pollIntervalMillis < 0) {
            throw new IllegalArgumentException("pollIntervalMillis cannot be negative");
        }
        this.maxPollAttempts = maxPollAttempts;
        this.pollIntervalMillis = pollIntervalMillis;
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            RequestDeadline deadline = RequestDeadline.start(context, nanoTime);
            deadline.check();
            verifyTableExists(deadline);
            Map<Metric, String> queryIds = startQueries(deadline);
            waitForQueries(queryIds, deadline);
            AnalyticsSummary summary = readSummary(queryIds, deadline);
            deadline.check();
            return AnalyticsApiResponses.json(json, 200, summary);
        } catch (EntityNotFoundException | MissingAnalyticsDataException exception) {
            log(context, exception);
            return AnalyticsApiResponses.error(json, 404, "No analytics data yet.");
        } catch (AnalyticsQueryTimeoutException | ApiCallTimeoutException
                | ApiCallAttemptTimeoutException exception) {
            log(context, exception);
            return AnalyticsApiResponses.error(json, 504, "Analytics query timed out.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log(context, exception);
            return AnalyticsApiResponses.error(json, 500, "Analytics query was interrupted.");
        } catch (SdkException | AnalyticsQueryException exception) {
            log(context, exception);
            return AnalyticsApiResponses.error(json, 502, "Unable to query analytics data.");
        } catch (RuntimeException exception) {
            log(context, exception);
            return AnalyticsApiResponses.error(json, 500, "Unable to process analytics query.");
        }
    }

    private void verifyTableExists(RequestDeadline deadline) {
        deadline.check();
        var response = glue.getTable(GetTableRequest.builder()
                .databaseName(config.database())
                .name(config.table())
                .build());
        deadline.check();
        if (response == null || response.table() == null) {
            throw new MissingAnalyticsDataException();
        }
    }

    private Map<Metric, String> startQueries(RequestDeadline deadline) {
        Map<Metric, String> queryIds = new EnumMap<>(Metric.class);
        for (Map.Entry<Metric, String> query : sqlQueries().entrySet()) {
            deadline.check();
            String executionId = athena.startQueryExecution(StartQueryExecutionRequest.builder()
                    .queryString(query.getValue())
                    .queryExecutionContext(QueryExecutionContext.builder()
                            .catalog("AwsDataCatalog")
                            .database(config.database())
                            .build())
                    .resultConfiguration(ResultConfiguration.builder()
                            .outputLocation(config.outputLocation())
                            .build())
                    .workGroup(config.workgroup())
                    .build()).queryExecutionId();
            deadline.check();
            if (executionId == null || executionId.isBlank()) {
                throw new AnalyticsQueryException("Athena did not return a query execution ID");
            }
            queryIds.put(query.getKey(), executionId);
        }
        return queryIds;
    }

    private void waitForQueries(Map<Metric, String> queryIds, RequestDeadline deadline)
            throws InterruptedException {
        Set<Metric> succeeded = EnumSet.noneOf(Metric.class);
        for (int attempt = 0; attempt < maxPollAttempts; attempt++) {
            for (Map.Entry<Metric, String> query : queryIds.entrySet()) {
                if (succeeded.contains(query.getKey())) continue;

                deadline.check();
                var execution = athena.getQueryExecution(GetQueryExecutionRequest.builder()
                        .queryExecutionId(query.getValue())
                        .build()).queryExecution();
                deadline.check();
                if (execution == null || execution.status() == null) {
                    throw new AnalyticsQueryException("Athena returned an invalid execution status");
                }

                QueryExecutionState state = execution.status().state();
                if (state == QueryExecutionState.SUCCEEDED) {
                    succeeded.add(query.getKey());
                } else if (state == QueryExecutionState.FAILED
                        || state == QueryExecutionState.CANCELLED) {
                    throw new AnalyticsQueryException("Athena query did not succeed");
                } else if (state != QueryExecutionState.QUEUED
                        && state != QueryExecutionState.RUNNING) {
                    throw new AnalyticsQueryException("Athena returned an unknown execution state");
                }
            }

            if (succeeded.size() == queryIds.size()) return;
            if (attempt + 1 < maxPollAttempts) {
                deadline.checkCanWait(pollIntervalMillis);
                sleeper.sleep(pollIntervalMillis);
                deadline.check();
            }
        }
        throw new AnalyticsQueryTimeoutException();
    }

    private AnalyticsSummary readSummary(
            Map<Metric, String> queryIds,
            RequestDeadline deadline) {
        long totalPosts = parseTotal(results(queryIds.get(Metric.TOTAL), deadline));
        Double average = parseAverage(results(queryIds.get(Metric.AVERAGE), deadline));
        List<RatingDistribution> distribution = parseDistribution(
                results(queryIds.get(Metric.DISTRIBUTION), deadline));
        List<LocationPostCount> locations = parseLocations(
                results(queryIds.get(Metric.LOCATIONS), deadline));
        return new AnalyticsSummary(totalPosts, average, distribution, locations,
                clock.instant().toString());
    }

    private GetQueryResultsResponse results(String queryId, RequestDeadline deadline) {
        deadline.check();
        GetQueryResultsResponse response = athena.getQueryResults(GetQueryResultsRequest.builder()
                .queryExecutionId(queryId)
                .build());
        deadline.check();
        return response;
    }

    static ClientOverrideConfiguration sdkTimeoutConfiguration() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(SDK_API_CALL_TIMEOUT)
                .apiCallAttemptTimeout(SDK_API_CALL_ATTEMPT_TIMEOUT)
                .build();
    }

    private static long parseTotal(GetQueryResultsResponse response) {
        List<Row> rows = dataRows(response, "total_posts");
        if (rows.isEmpty()) return 0;
        return parseLong(cell(rows.getFirst(), 0), "total_posts");
    }

    private static Double parseAverage(GetQueryResultsResponse response) {
        List<Row> rows = dataRows(response, "average_accuracy");
        if (rows.isEmpty()) return null;
        String value = cell(rows.getFirst(), 0);
        if (value == null || value.isBlank()) return null;
        try {
            double average = Double.parseDouble(value);
            if (!Double.isFinite(average)) throw new NumberFormatException("not finite");
            return average;
        } catch (NumberFormatException exception) {
            throw new AnalyticsQueryException("Invalid average_accuracy result", exception);
        }
    }

    private static List<RatingDistribution> parseDistribution(GetQueryResultsResponse response) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) counts.put(rating, 0L);

        for (Row row : dataRows(response, "rating")) {
            long rawRating = parseLong(cell(row, 0), "rating");
            if (rawRating >= 1 && rawRating <= 5) {
                counts.put((int) rawRating, parseLong(cell(row, 1), "post_count"));
            }
        }

        return counts.entrySet().stream()
                .map(entry -> new RatingDistribution(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<LocationPostCount> parseLocations(GetQueryResultsResponse response) {
        List<LocationPostCount> locations = new ArrayList<>();
        for (Row row : dataRows(response, "location_name")) {
            String name = cell(row, 0);
            if (name == null || name.isBlank()) {
                throw new AnalyticsQueryException("Invalid location_name result");
            }
            locations.add(new LocationPostCount(name, parseLong(cell(row, 1), "post_count")));
        }
        return List.copyOf(locations);
    }

    private static List<Row> dataRows(GetQueryResultsResponse response, String firstHeader) {
        if (response == null || response.resultSet() == null || response.resultSet().rows() == null) {
            throw new AnalyticsQueryException("Athena returned an invalid result set");
        }
        List<Row> rows = response.resultSet().rows();
        if (rows.isEmpty()) return List.of();
        String firstCell = cell(rows.getFirst(), 0);
        int start = firstCell != null && firstHeader.equalsIgnoreCase(firstCell) ? 1 : 0;
        return rows.subList(start, rows.size());
    }

    private static String cell(Row row, int index) {
        if (row == null || row.data() == null || index >= row.data().size()) return null;
        Datum datum = row.data().get(index);
        return datum == null ? null : datum.varCharValue();
    }

    private static long parseLong(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new AnalyticsQueryException("Missing " + name + " result");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new AnalyticsQueryException("Invalid " + name + " result", exception);
        }
    }

    Map<Metric, String> sqlQueries() {
        String source = quote(config.database()) + "." + quote(config.table());
        String rating = quote(config.ratingColumn());
        String location = quote(config.locationColumn());
        Map<Metric, String> queries = new LinkedHashMap<>();
        queries.put(Metric.TOTAL, "SELECT COUNT(*) AS total_posts FROM " + source + ";");
        queries.put(Metric.AVERAGE,
                "SELECT ROUND(AVG(" + rating
                        + "), 2) AS average_accuracy FROM " + source + ";");
        queries.put(Metric.DISTRIBUTION,
                "SELECT " + rating + " AS rating, COUNT(*) AS post_count FROM "
                        + source + " GROUP BY " + rating
                        + " ORDER BY " + rating + ";");
        queries.put(Metric.LOCATIONS,
                "SELECT " + location + " AS location_name, COUNT(*) AS post_count FROM " + source
                        + " GROUP BY " + location
                        + " ORDER BY post_count DESC LIMIT 5;");
        return queries;
    }

    private static String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    private static void log(Context context, Exception exception) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(exception.getClass().getSimpleName() + ": "
                    + String.valueOf(exception.getMessage()) + System.lineSeparator());
        }
    }

    enum Metric {
        TOTAL,
        AVERAGE,
        DISTRIBUTION,
        LOCATIONS
    }

    record Config(
            String database,
            String table,
            String ratingColumn,
            String locationColumn,
            String outputLocation,
            String workgroup,
            Region region) {

        Config {
            database = requireIdentifier(database, "ATHENA_DATABASE");
            table = requireIdentifier(table, "ATHENA_TABLE");
            ratingColumn = requireIdentifier(ratingColumn, "ATHENA_RATING_COLUMN");
            locationColumn = requireIdentifier(locationColumn, "ATHENA_LOCATION_COLUMN");
            outputLocation = requireOutputLocation(outputLocation);
            if (!outputLocation.endsWith("/")) outputLocation += "/";
            workgroup = requireValue(workgroup, "ATHENA_WORKGROUP");
            Objects.requireNonNull(region, "AWS_REGION is required");
        }

        static Config fromEnvironment(Map<String, String> environment) {
            String rawRegion = requireValue(environment.get("AWS_REGION"), "AWS_REGION");
            return new Config(
                    environment.get("ATHENA_DATABASE"),
                    environment.get("ATHENA_TABLE"),
                    environment.getOrDefault("ATHENA_RATING_COLUMN", "weatheraccuracyrating"),
                    environment.getOrDefault("ATHENA_LOCATION_COLUMN", "locationname"),
                    environment.get("ATHENA_OUTPUT_LOCATION"),
                    environment.getOrDefault("ATHENA_WORKGROUP", "primary"),
                    Region.of(rawRegion));
        }
    }

    private static String requireIdentifier(String value, String name) {
        String identifier = requireValue(value, name);
        if (!ATHENA_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(name + " must be a valid Athena identifier");
        }
        return identifier;
    }

    private static String requireOutputLocation(String value) {
        String location = requireValue(value, "ATHENA_OUTPUT_LOCATION");
        try {
            URI uri = new URI(location);
            if (!"s3".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getPath() == null
                    || uri.getPath().length() < 2) {
                throw new IllegalArgumentException(
                        "ATHENA_OUTPUT_LOCATION must be an s3:// bucket/prefix URI");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "ATHENA_OUTPUT_LOCATION must be an s3:// bucket/prefix URI", exception);
        }
        if (location.toLowerCase(Locale.ROOT).contains("/community-analytics/posts")) {
            throw new IllegalArgumentException(
                    "ATHENA_OUTPUT_LOCATION must not use the analytics source prefix");
        }
        return location;
    }

    private static String requireValue(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    /**
     * Monotonic request deadline that leaves enough time for a controlled HTTP
     * response before either Lambda or API Gateway terminates the invocation.
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
            long workBudgetMillis = Math.min(
                    REQUEST_WORK_BUDGET_MILLIS, lambdaBudgetMillis);
            long deadlineNanos = now + Duration.ofMillis(workBudgetMillis).toNanos();
            return new RequestDeadline(nanoTime, deadlineNanos);
        }

        void check() {
            if (deadlineNanos - nanoTime.getAsLong() <= 0) {
                throw new AnalyticsQueryTimeoutException();
            }
        }

        void checkCanWait(long waitMillis) {
            long waitNanos = Duration.ofMillis(waitMillis).toNanos();
            if (deadlineNanos - nanoTime.getAsLong() <= waitNanos) {
                throw new AnalyticsQueryTimeoutException();
            }
        }
    }

    record AnalyticsSummary(
            long totalPosts,
            Double averageWeatherAccuracy,
            List<RatingDistribution> ratingDistribution,
            List<LocationPostCount> topLocations,
            String generatedAt) {}

    record RatingDistribution(int rating, long count) {}

    record LocationPostCount(String locationName, long postCount) {}

    static class AnalyticsQueryException extends RuntimeException {
        AnalyticsQueryException(String message) {
            super(message);
        }

        AnalyticsQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class AnalyticsQueryTimeoutException extends AnalyticsQueryException {
        AnalyticsQueryTimeoutException() {
            super("Athena query polling timed out");
        }
    }

    static final class MissingAnalyticsDataException extends RuntimeException {}
}
