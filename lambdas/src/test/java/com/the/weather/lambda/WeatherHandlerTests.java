package com.the.weather.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WeatherHandlerTests {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-06T00:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void forecastHandlerReturnsCurrentWeatherWhenDateTimeIsAbsent() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        ForecastWeatherHandler handler = new ForecastWeatherHandler(
                uri -> {
                    requestedUri.set(uri);
                    return new OpenMeteoWeatherHandler.ProviderResponse(
                            200, successfulCurrentResponse());
                },
                JSON,
                CLOCK,
                "https://forecast.test");

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "10.729",
                "longitude", "106.694",
                "timezone", "Asia/Ho_Chi_Minh")), null);

        assertEquals(200, response.getStatusCode());
        assertTrue(requestedUri.get().toString().contains("current=temperature_2m"));
        assertTrue(!requestedUri.get().toString().contains("hourly="));
        assertTrue(!requestedUri.get().toString().contains("start_date="));

        JsonNode body = JSON.readTree(response.getBody());
        assertEquals("2026-09-06T14:15", body.path("recordedAt").asText());
        assertEquals(31.4, body.path("temperature_2m").asDouble());
        assertEquals(2, body.path("weather_code").asInt());
        assertEquals("Partly cloudy", body.path("condition").asText());
    }

    @Test
    void forecastHandlerAlwaysCallsTheForecastEndpoint() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        ForecastWeatherHandler handler = new ForecastWeatherHandler(
                successfulTransport(requestedUri), JSON, CLOCK, "https://forecast.test");

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "lat", "10.729",
                "lon", "106.694",
                "date", "2026-09-06",
                "time", "10:35",
                "timezone", "Asia/Ho_Chi_Minh")), null);

        assertEquals(200, response.getStatusCode());
        assertTrue(requestedUri.get().toString().startsWith("https://forecast.test?"));
        assertTrue(requestedUri.get().toString().contains("latitude=10.729"));
        assertTrue(requestedUri.get().toString().contains("longitude=106.694"));
        assertTrue(requestedUri.get().toString().contains("timezone=Asia%2FHo_Chi_Minh"));

        JsonNode body = JSON.readTree(response.getBody());
        assertEquals("2026-09-06T10:35", body.path("requestedAt").asText());
        assertEquals("2026-09-06T11:00", body.path("recordedAt").asText());
        assertEquals(31.2, body.path("temperature_2m").asDouble());
    }

    @Test
    void historicalHandlerAlwaysCallsTheArchiveEndpoint() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        HistoricalWeatherHandler handler = new HistoricalWeatherHandler(
                successfulTransport(requestedUri), JSON, CLOCK, "https://archive.test");

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "10.729",
                "longitude", "106.694",
                "dateTime", "2020-01-01T10:00")), null);

        assertEquals(200, response.getStatusCode());
        assertTrue(requestedUri.get().toString().startsWith("https://archive.test?"));
        assertTrue(requestedUri.get().toString().contains("start_date=2020-01-01"));
    }

    @Test
    void forecastHandlerRejectsPastDatesWithoutCallingOpenMeteo() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        ForecastWeatherHandler handler = new ForecastWeatherHandler(
                successfulTransport(requestedUri), JSON, CLOCK, "https://forecast.test");

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "10.729",
                "longitude", "106.694",
                "dateTime", "2020-01-01T10:00")), null);

        assertEquals(400, response.getStatusCode());
        assertTrue(JSON.readTree(response.getBody()).path("message").asText()
                .contains("Forecast dateTime"));
        assertNull(requestedUri.get());
    }

    @Test
    void historicalHandlerRejectsFutureDatesWithoutCallingOpenMeteo() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        HistoricalWeatherHandler handler = new HistoricalWeatherHandler(
                successfulTransport(requestedUri), JSON, CLOCK, "https://archive.test");

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "10.729",
                "longitude", "106.694",
                "dateTime", "2026-09-07T10:00")), null);

        assertEquals(400, response.getStatusCode());
        assertTrue(JSON.readTree(response.getBody()).path("message").asText()
                .contains("Historical dateTime"));
        assertNull(requestedUri.get());
    }

    @Test
    void historicalHandlerRequiresDateTime() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        HistoricalWeatherHandler handler = new HistoricalWeatherHandler(
                successfulTransport(requestedUri), JSON, CLOCK, "https://archive.test");

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "10.729",
                "longitude", "106.694")), null);

        assertEquals(400, response.getStatusCode());
        assertTrue(JSON.readTree(response.getBody()).path("message").asText()
                .contains("required for historical weather"));
        assertNull(requestedUri.get());
    }

    @Test
    void rejectsInvalidCoordinatesWithoutCallingOpenMeteo() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        ForecastWeatherHandler handler = new ForecastWeatherHandler(
                successfulTransport(requestedUri), JSON, CLOCK, "https://forecast.test");

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "91",
                "longitude", "106.694",
                "dateTime", "2026-09-06T10:00")), null);

        assertEquals(400, response.getStatusCode());
        assertTrue(JSON.readTree(response.getBody()).path("message").asText()
                .contains("latitude must be between"));
        assertNull(requestedUri.get());
    }

    private static OpenMeteoWeatherHandler.HttpTransport successfulTransport(
            AtomicReference<URI> requestedUri) {
        return uri -> {
            requestedUri.set(uri);
            return new OpenMeteoWeatherHandler.ProviderResponse(200, successfulResponse());
        };
    }

    private static APIGatewayV2HTTPEvent event(Map<String, String> query) {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setQueryStringParameters(query);
        return event;
    }

    private static String successfulResponse() {
        return """
                {
                  "timezone": "Asia/Ho_Chi_Minh",
                  "hourly": {
                    "time": ["2026-09-06T10:00", "2026-09-06T11:00"],
                    "temperature_2m": [30.5, 31.2],
                    "relative_humidity_2m": [75, 72],
                    "apparent_temperature": [34.0, 34.8],
                    "precipitation": [0.0, 0.1],
                    "rain": [0.0, 0.1],
                    "is_day": [1, 1],
                    "wind_speed_10m": [8.2, 9.1],
                    "wind_direction_10m": [180, 190],
                    "weather_code": [1, 2]
                  }
                }
                """;
    }

    private static String successfulCurrentResponse() {
        return """
                {
                  "timezone": "Asia/Ho_Chi_Minh",
                  "current": {
                    "time": "2026-09-06T14:15",
                    "temperature_2m": 31.4,
                    "relative_humidity_2m": 71,
                    "apparent_temperature": 35.1,
                    "precipitation": 0.0,
                    "rain": 0.0,
                    "is_day": 1,
                    "wind_speed_10m": 8.7,
                    "wind_direction_10m": 185,
                    "weather_code": 2
                  }
                }
                """;
    }
}
