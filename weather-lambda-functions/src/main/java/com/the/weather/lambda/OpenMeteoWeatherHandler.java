package com.the.weather.lambda;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;       // request from api gateway
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;    // response form api gateway
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

// open-meteo based/shared handler: request, validation, and response-normalization logic
// flow: api gateway request -> handle request -> parse request -> build uri -> calls open-meteo -> normalized returned json responses
public abstract class OpenMeteoWeatherHandler implements
        RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    
    // defines which  open-meteo variables this handler requests
    private static final List<String> WEATHER_FIELDS = List.of(
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "precipitation",
            "rain",
            "cloud_cover",
            "is_day",
            "wind_speed_10m",
            "wind_direction_10m",
            "weather_code");

    private static final List<String> DAILY_FIELDS = List.of(
            "temperature_2m_max",
            "temperature_2m_min",
            "sunrise",
            "sunset",
            "precipitation_probability_max",
            "weather_code");

    private static final List<String> HOURLY_FIELDS = List.of(
            "temperature_2m",
            "precipitation_probability",
            "weather_code",
            "uv_index",
            "uv_index_clear_sky",
            "is_day");

    private static final int DAILY_FORECAST_DAYS = 7;
    private static final int HOURLY_FORECAST_DAYS = 1;

    // block historical requests that are too old
    private static final LocalDate EARLIEST_ARCHIVE_DATE = LocalDate.of(1940, 1, 1);

    // only forecast within 10 days
    private static final int MAX_FORECAST_DAYS = 10;

    // parse/create JSON
    private final ObjectMapper json;            
    private final HttpTransport transport;
    private final Clock clock;
    private final String openMeteoUrl;

    // identifies what type of handler is being used: forecast or historical
    private final WeatherDataType dataType;
    
    // production constructor: automatically creates the real HTTP transport, JSON parser, and clock
    OpenMeteoWeatherHandler(String openMeteoUrl, WeatherDataType dataType) {
        this(defaultTransport(), new ObjectMapper(), Clock.systemUTC(), openMeteoUrl, dataType);
    }

    // constructor: allows dependencies to be injected, which is useful for testing
    OpenMeteoWeatherHandler(
            HttpTransport transport,
            ObjectMapper json,
            Clock clock,
            String openMeteoUrl,
            WeatherDataType dataType) {
        this.transport = transport;
        this.json = json;
        this.clock = clock;
        this.openMeteoUrl = openMeteoUrl;
        this.dataType = dataType;
    }

    @Override
    // main entry point of aws lambda
    // api gateway converts http request to APIGatewayV2HTTPEvent and passes to event
    // event contains the http request from API Gateway
    public final APIGatewayV2HTTPResponse handleRequest(
            APIGatewayV2HTTPEvent event,
            Context context) {
        try {
            // convert raw api gateway request into clean validated java object
            WeatherRequest input = parseRequest(event);

            // build the open-meteo uri then send actual http request
            ProviderResponse providerResponse = transport.get(buildOpenMeteoUri(input));
            
            // check open-meteo status
            if (providerResponse.statusCode() != 200) {
                return error(502, "Open-Meteo returned HTTP " + providerResponse.statusCode());
            }
            
            // wrap json inside api gaaetway http response
            // convert open-meteo response into our application's response format
            return jsonResponse(200, normalize(providerResponse.body(), input));
        } catch (IllegalArgumentException exception) {
            return error(400, exception.getMessage());
        } catch (HttpTimeoutException exception) {
            log(context, exception);
            return error(504, "Open-Meteo request timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log(context, exception);
            return error(500, "Weather request was interrupted");
        } catch (IOException exception) {
            log(context, exception);
            return error(502, providerFailureMessage());
        }
    }

    private String providerFailureMessage() {
        return dataType == WeatherDataType.HOURLY
                ? "Unable to retrieve hourly weather data"
                : "Unable to retrieve weather data";
    }

    // read latitude, longitude, date/time, and timezone -> validate before sent to open-meteo
    private WeatherRequest parseRequest(APIGatewayV2HTTPEvent event) {
        
        // check null request
        Map<String, String> query = event == null ? null : event.getQueryStringParameters();
        if (query == null) {
            throw new IllegalArgumentException("latitude and longitude are required");
        }

        // chooses the first value if it is non-blank; otherwise, it falls back to the second value
        double latitude = parseCoordinate(
                firstNonBlank(query.get("latitude"), query.get("lat")),
                "latitude",
                -90,
                90);
        double longitude = parseCoordinate(
                firstNonBlank(query.get("longitude"), query.get("lon")),
                "longitude",
                -180,
                180);
        LocalDateTime dateTime = dataType == WeatherDataType.HOURLY
                ? null
                : parseOptionalDateTime(query);
        String timezone = parseTimezone(query.get("timezone"));
        
        // reject missing date for historical data
        if (dateTime == null && dataType == WeatherDataType.HISTORICAL) {
            throw new IllegalArgumentException(
                    "dateTime or the date and time parameters are required for historical weather");
        }
        if (dateTime != null) {
            validateDate(dateTime.toLocalDate());
        }
        return new WeatherRequest(latitude, longitude, dateTime, timezone);
    }

    // check date if in range or not
    private void validateDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (dataType == WeatherDataType.FORECAST
                && (date.isBefore(today) || date.isAfter(today.plusDays(MAX_FORECAST_DAYS)))) {
            throw new IllegalArgumentException(
                    "Forecast dateTime must be between " + today + " and "
                            + today.plusDays(MAX_FORECAST_DAYS));
        }
        if (dataType == WeatherDataType.HISTORICAL
                && (date.isBefore(EARLIEST_ARCHIVE_DATE) || date.isAfter(today))) {
            throw new IllegalArgumentException(
                    "Historical dateTime must be between " + EARLIEST_ARCHIVE_DATE
                            + " and " + today);
        }
    }

    // reject missing or blank values
    // convert to the correct format
    private static double parseCoordinate(
            String rawValue,
            String name,
            double minimum,
            double maximum) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }

        try {
            double value = Double.parseDouble(rawValue);
            if (!Double.isFinite(value) || value < minimum || value > maximum) {
                throw new IllegalArgumentException(
                        name + " must be between " + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a valid number");
        }
    }

    // validate the date format
    private static LocalDateTime parseOptionalDateTime(Map<String, String> query) {
        String rawValue = dateTimeValue(query);
        if (rawValue == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(rawValue.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "dateTime must use ISO-8601 local format, for example 2026-09-06T14:30");
        }
    }

    // validate the date and time are required together
    private static String dateTimeValue(Map<String, String> query) {
        String dateTime = query.get("dateTime");
        if (dateTime != null && !dateTime.isBlank()) {
            return dateTime;
        }

        String date = query.get("date");
        String time = query.get("time");
        if ((date == null || date.isBlank()) && (time == null || time.isBlank())) {
            return null;
        }
        if (date == null || date.isBlank() || time == null || time.isBlank()) {
            throw new IllegalArgumentException("date and time must be provided together");
        }
        return date.trim() + "T" + time.trim();
    }

    // check does the app provide the times or not, if not auto detect the time by lat/lon
    private static String parseTimezone(String rawValue) {
        String timezone = rawValue == null || rawValue.isBlank() ? "auto" : rawValue.trim();
        if ("auto".equals(timezone)) {
            return timezone;
        }

        try {
            ZoneId.of(timezone);
            return timezone;
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(
                    "timezone must be auto or a valid IANA timezone such as Asia/Ho_Chi_Minh");
        }
    }

    // build ther uri
    private URI buildOpenMeteoUri(WeatherRequest input) {
        return switch (dataType) {
            case FORECAST -> buildForecastUri(input);
            case HOURLY -> buildHourlyUri(input);
            case DAILY -> buildDailyUri(input);
            case HISTORICAL -> buildHistoricalUri(input);
        };
    }

    private URI buildForecastUri(WeatherRequest input) {
        // if not having date -> requests the current weather conditions
        if (input.dateTime() == null) {
            return buildCurrentUri(input);
        }
        return buildWeatherAtTimeUri(input);
    }

    private URI buildCurrentUri(WeatherRequest input) {
        return URI.create(String.format(
                Locale.ROOT,
                "%s?latitude=%s&longitude=%s&current=%s&timezone=%s",
                openMeteoUrl,
                input.latitude(),
                input.longitude(),
                String.join(",", WEATHER_FIELDS),
                encode(input.timezone())));
    }

    private URI buildHourlyUri(WeatherRequest input) {
        return URI.create(String.format(
                Locale.ROOT,
                "%s?latitude=%s&longitude=%s&hourly=%s&forecast_days=%d&timezone=%s",
                openMeteoUrl,
                input.latitude(),
                input.longitude(),
                String.join(",", HOURLY_FIELDS),
                HOURLY_FORECAST_DAYS,
                encode(input.timezone())));
    }

    private URI buildDailyUri(WeatherRequest input) {
        return URI.create(String.format(
                Locale.ROOT,
                "%s?latitude=%s&longitude=%s&daily=%s"
                        + "&forecast_days=%d&timezone=%s",
                openMeteoUrl,
                input.latitude(),
                input.longitude(),
                String.join(",", DAILY_FIELDS),
                DAILY_FORECAST_DAYS,
                encode(input.timezone())));
    }

    private URI buildHistoricalUri(WeatherRequest input) {
        return buildWeatherAtTimeUri(input);
    }

    private URI buildWeatherAtTimeUri(WeatherRequest input) {
        String date = input.dateTime().toLocalDate().toString();
        return URI.create(String.format(
                Locale.ROOT,
                "%s?latitude=%s&longitude=%s&hourly=%s"
                        + "&start_date=%s&end_date=%s&timezone=%s",
                openMeteoUrl,
                input.latitude(),
                input.longitude(),
                String.join(",", WEATHER_FIELDS),
                date,
                date,
                encode(input.timezone())));
    }

    private ObjectNode normalize(String responseBody, WeatherRequest input) throws IOException {
        JsonNode root = json.readTree(responseBody);
        return switch (dataType) {
            case FORECAST -> input.dateTime() == null
                    ? normalizeCurrent(root, input)
                    : normalizeForecastAtTime(root, input);
            case HOURLY -> normalizeHourly(root, input);
            case DAILY -> normalizeDaily(root, input);
            case HISTORICAL -> normalizeHistorical(root, input);
        };
    }

    private ObjectNode normalizeForecastAtTime(JsonNode root, WeatherRequest input)
            throws IOException {
        return normalizeWeatherAtTime(root, input);
    }

    private ObjectNode normalizeHistorical(JsonNode root, WeatherRequest input)
            throws IOException {
        return normalizeWeatherAtTime(root, input);
    }

    private ObjectNode normalizeWeatherAtTime(JsonNode root, WeatherRequest input)
            throws IOException {

        JsonNode hourly = root.path("hourly");
        JsonNode times = hourly.path("time");
        if (!hourly.isObject() || !times.isArray() || times.isEmpty()) {
            throw new IOException("Open-Meteo returned an invalid hourly response");
        }

        int closestIndex = findClosestTimeIndex(times, input.dateTime());
        ObjectNode result = json.createObjectNode();
        result.put("latitude", input.latitude());
        result.put("longitude", input.longitude());
        result.put("requestedAt", input.dateTime().toString());
        result.put("recordedAt", times.get(closestIndex).asText());
        result.put("timezone", root.path("timezone").asText(input.timezone()));

        for (String field : WEATHER_FIELDS) {
            JsonNode values = hourly.path(field);
            JsonNode value = values.isArray() && closestIndex < values.size()
                    ? values.get(closestIndex)
                    : null;
            if (value == null || !value.isNumber()) {
                throw new IOException("Open-Meteo response is missing " + field);
            }
            result.set(field, value);
        }
        result.put("condition", weatherCondition(result.path("weather_code").asInt()));

        return result;
    }

    private ObjectNode normalizeHourly(JsonNode root, WeatherRequest input) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IOException("Open-Meteo returned an invalid hourly response");
        }
        JsonNode hourly = root.path("hourly");
        JsonNode times = hourly.path("time");
        if (!hourly.isObject() || !times.isArray() || times.isEmpty()) {
            throw new IOException("Open-Meteo returned an invalid hourly response");
        }

        int hourCount = times.size();
        validateHourlyArrays(hourly, hourCount);

        ArrayNode hours = json.createArrayNode();
        for (int index = 0; index < hourCount; index++) {
            JsonNode timestamp = times.get(index);
            if (!timestamp.isTextual()) {
                throw new IOException("Open-Meteo response contains an invalid hourly timestamp");
            }
            String recordedAt = timestamp.textValue();
            try {
                LocalDateTime.parse(recordedAt);
            } catch (DateTimeParseException exception) {
                throw new IOException(
                        "Open-Meteo response contains an invalid hourly timestamp",
                        exception);
            }

            ObjectNode hour = hours.addObject();
            hour.put("recordedAt", recordedAt);
            for (String field : HOURLY_FIELDS) {
                hour.set(field, hourly.path(field).get(index));
            }
            hour.put("condition", weatherCondition(hour.path("weather_code").asInt()));
        }

        ObjectNode result = json.createObjectNode();
        result.put("latitude", input.latitude());
        result.put("longitude", input.longitude());
        result.put("timezone", root.path("timezone").asText(input.timezone()));
        result.set("hourly", hours);
        return result;
    }

    private static void validateHourlyArrays(JsonNode hourly, int expectedSize)
            throws IOException {
        for (String field : HOURLY_FIELDS) {
            JsonNode values = hourly.path(field);
            if (!values.isArray() || values.size() != expectedSize) {
                throw new IOException(
                        "Open-Meteo response contains a mismatched " + field + " array");
            }
            for (JsonNode value : values) {
                if (!value.isNumber()) {
                    throw new IOException("Open-Meteo response is missing " + field);
                }
            }
        }
    }

    private ObjectNode normalizeDaily(JsonNode root, WeatherRequest input) throws IOException {
        JsonNode daily = root == null ? null : root.path("daily");
        JsonNode dates = daily == null ? null : daily.path("time");
        if (root == null || !root.isObject() || daily == null || !daily.isObject()
                || dates == null || !dates.isArray() || dates.isEmpty()) {
            throw new IOException("Open-Meteo returned an invalid daily response");
        }

        int dayCount = dates.size();
        validateDailyArrays(daily, dayCount);
        ArrayNode days = json.createArrayNode();
        for (int index = 0; index < dayCount; index++) {
            String date = textualValue(dates.get(index), "date");
            String sunrise = textualValue(daily.path("sunrise").get(index), "sunrise");
            String sunset = textualValue(daily.path("sunset").get(index), "sunset");
            try {
                LocalDate.parse(date);
                LocalDateTime.parse(sunrise);
                LocalDateTime.parse(sunset);
            } catch (DateTimeParseException exception) {
                throw new IOException("Open-Meteo response contains an invalid daily timestamp",
                        exception);
            }

            ObjectNode day = days.addObject();
            day.put("date", date);
            for (String field : DAILY_FIELDS) {
                if (!"sunrise".equals(field) && !"sunset".equals(field)) {
                    day.set(field, daily.path(field).get(index));
                }
            }
            day.put("sunrise", sunrise);
            day.put("sunset", sunset);
            day.put("condition", weatherCondition(day.path("weather_code").asInt()));
        }

        ObjectNode result = json.createObjectNode();
        result.put("latitude", input.latitude());
        result.put("longitude", input.longitude());
        result.put("timezone", root.path("timezone").asText(input.timezone()));
        result.set("daily", days);
        return result;
    }

    private static void validateDailyArrays(JsonNode daily, int expectedSize)
            throws IOException {
        for (String field : DAILY_FIELDS) {
            JsonNode values = daily.path(field);
            if (!values.isArray() || values.size() != expectedSize) {
                throw new IOException(
                        "Open-Meteo response contains a mismatched " + field + " array");
            }
            for (JsonNode value : values) {
                boolean valid = "sunrise".equals(field) || "sunset".equals(field)
                        ? value.isTextual()
                        : value.isNumber();
                if (!valid) {
                    throw new IOException("Open-Meteo response is missing " + field);
                }
            }
        }
    }

    private static String textualValue(JsonNode value, String field) throws IOException {
        if (value == null || !value.isTextual()) {
            throw new IOException("Open-Meteo response is missing " + field);
        }
        return value.textValue();
    }

    private ObjectNode normalizeCurrent(JsonNode root, WeatherRequest input) throws IOException {
        JsonNode current = root.path("current");
        String recordedAt = current.path("time").asText();
        if (!current.isObject() || recordedAt.isBlank()) {
            throw new IOException("Open-Meteo returned an invalid current response");
        }

        ObjectNode result = json.createObjectNode();
        result.put("latitude", input.latitude());
        result.put("longitude", input.longitude());
        result.put("requestedAt", recordedAt);
        result.put("recordedAt", recordedAt);
        result.put("timezone", root.path("timezone").asText(input.timezone()));

        for (String field : WEATHER_FIELDS) {
            JsonNode value = current.get(field);
            if (value == null || !value.isNumber()) {
                throw new IOException("Open-Meteo response is missing " + field);
            }
            result.set(field, value);
        }
        result.put("condition", weatherCondition(result.path("weather_code").asInt()));
        return result;
    }

    // convert weather code into condition based on WMO
    static String weatherCondition(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45 -> "Fog";
            case 48 -> "Depositing rime fog";
            case 51 -> "Light drizzle";
            case 53 -> "Moderate drizzle";
            case 55 -> "Dense drizzle";
            case 56 -> "Light freezing drizzle";
            case 57 -> "Dense freezing drizzle";
            case 61 -> "Slight rain";
            case 63 -> "Moderate rain";
            case 65 -> "Heavy rain";
            case 66 -> "Light freezing rain";
            case 67 -> "Heavy freezing rain";
            case 71 -> "Slight snowfall";
            case 73 -> "Moderate snowfall";
            case 75 -> "Heavy snowfall";
            case 77 -> "Snow grains";
            case 80 -> "Slight rain showers";
            case 81 -> "Moderate rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Slight snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Slight or moderate thunderstorm";
            case 96 -> "Thunderstorm with slight hail";
            case 99 -> "Thunderstorm with heavy hail";
            default -> "Unknown";
        };
    }

    private static int findClosestTimeIndex(JsonNode times, LocalDateTime requested)
            throws IOException {
        int closestIndex = -1;
        long closestMinutes = Long.MAX_VALUE;

        for (int index = 0; index < times.size(); index++) {
            try {
                LocalDateTime candidate = LocalDateTime.parse(times.get(index).asText());
                long difference = Math.abs(ChronoUnit.MINUTES.between(requested, candidate));
                if (difference < closestMinutes) {
                    closestMinutes = difference;
                    closestIndex = index;
                }
            } catch (DateTimeParseException ignored) {
                // Ignore malformed entries and use another valid hourly timestamp.
            }
        }

        if (closestIndex < 0) {
            throw new IOException("Open-Meteo response contains no valid hourly timestamps");
        }
        return closestIndex;
    }

    static HttpTransport defaultTransport() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "TheWeather/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());
            return new ProviderResponse(response.statusCode(), response.body());
        };
    }

    static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private APIGatewayV2HTTPResponse error(int statusCode, String message) {
        ObjectNode body = json.createObjectNode();
        body.put("message", message);
        return jsonResponse(statusCode, body);
    }

    private APIGatewayV2HTTPResponse jsonResponse(int statusCode, JsonNode body) {
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setStatusCode(statusCode);
        response.setHeaders(Map.of("content-type", "application/json"));
        response.setBody(body.toString());
        return response;
    }

    private static void log(Context context, Exception exception) {
        if (context != null) {
            context.getLogger().log(
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    enum WeatherDataType {
        FORECAST,
        HISTORICAL,
        HOURLY,
        DAILY
    }

    record WeatherRequest(
            double latitude,
            double longitude,
            LocalDateTime dateTime,
            String timezone) {
    }

    record ProviderResponse(int statusCode, String body) {
    }

    @FunctionalInterface
    interface HttpTransport {
        ProviderResponse get(URI uri) throws IOException, InterruptedException;
    }
}
