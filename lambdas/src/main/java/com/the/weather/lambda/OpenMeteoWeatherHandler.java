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
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Shared Open-Meteo request, validation, and response-normalization logic. */
public abstract class OpenMeteoWeatherHandler implements
        RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final List<String> WEATHER_FIELDS = List.of(
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "precipitation",
            "rain",
            "is_day",
            "wind_speed_10m",
            "wind_direction_10m",
            "weather_code");

    private static final LocalDate EARLIEST_ARCHIVE_DATE = LocalDate.of(1940, 1, 1);
    private static final int MAX_FORECAST_DAYS = 16;

    private final ObjectMapper json;
    private final HttpTransport transport;
    private final Clock clock;
    private final String openMeteoUrl;
    private final WeatherDataType dataType;

    OpenMeteoWeatherHandler(String openMeteoUrl, WeatherDataType dataType) {
        this(defaultTransport(), new ObjectMapper(), Clock.systemUTC(), openMeteoUrl, dataType);
    }

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
    public final APIGatewayV2HTTPResponse handleRequest(
            APIGatewayV2HTTPEvent event,
            Context context) {
        try {
            WeatherRequest input = parseRequest(event);
            ProviderResponse providerResponse = transport.get(buildOpenMeteoUri(input));

            if (providerResponse.statusCode() != 200) {
                return error(502, "Open-Meteo returned HTTP " + providerResponse.statusCode());
            }

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
            return error(502, "Unable to retrieve weather data");
        }
    }

    private WeatherRequest parseRequest(APIGatewayV2HTTPEvent event) {
        Map<String, String> query = event == null ? null : event.getQueryStringParameters();
        if (query == null) {
            throw new IllegalArgumentException("latitude and longitude are required");
        }

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
        LocalDateTime dateTime = parseOptionalDateTime(query);
        String timezone = parseTimezone(query.get("timezone"));

        if (dateTime == null && dataType == WeatherDataType.HISTORICAL) {
            throw new IllegalArgumentException(
                    "dateTime or the date and time parameters are required for historical weather");
        }
        if (dateTime != null) {
            validateDate(dateTime.toLocalDate());
        }
        return new WeatherRequest(latitude, longitude, dateTime, timezone);
    }

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

    private URI buildOpenMeteoUri(WeatherRequest input) {
        if (input.dateTime() == null) {
            return URI.create(String.format(
                    Locale.ROOT,
                    "%s?latitude=%s&longitude=%s&current=%s&timezone=%s",
                    openMeteoUrl,
                    input.latitude(),
                    input.longitude(),
                    String.join(",", WEATHER_FIELDS),
                    encode(input.timezone())));
        }

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
        if (input.dateTime() == null) {
            return normalizeCurrent(root, input);
        }

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

    private static String weatherCondition(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snowfall";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
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
        HISTORICAL
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
