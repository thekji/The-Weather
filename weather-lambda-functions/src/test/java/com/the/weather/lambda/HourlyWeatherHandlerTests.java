package com.the.weather.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HourlyWeatherHandlerTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void returnsOneDayOfNormalizedHourlyWeatherVariables() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        HourlyWeatherHandler handler = handler(requestedUri, successfulResponse());

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "lat", "10.729",
                "lon", "106.694",
                "timezone", "Asia/Ho_Chi_Minh")), null);

        assertEquals(200, response.getStatusCode());
        String uri = requestedUri.get().toString();
        assertTrue(uri.startsWith("https://forecast.test?"));
        assertTrue(uri.contains("latitude=10.729"));
        assertTrue(uri.contains("longitude=106.694"));
        assertTrue(uri.contains("forecast_days=1"));
        assertTrue(uri.contains("precipitation_probability"));
        assertFalse(uri.contains("relative_humidity_2m"));
        assertFalse(uri.contains("evapotranspiration"));
        assertFalse(uri.contains("wind_speed_10m"));
        assertTrue(uri.contains("uv_index"));
        assertTrue(uri.contains("uv_index_clear_sky"));
        assertTrue(uri.contains("timezone=Asia%2FHo_Chi_Minh"));

        JsonNode body = JSON.readTree(response.getBody());
        assertEquals(10.729, body.path("latitude").asDouble());
        assertEquals(106.694, body.path("longitude").asDouble());
        assertEquals("Asia/Ho_Chi_Minh", body.path("timezone").asText());
        assertEquals(2, body.path("hourly").size());

        JsonNode firstHour = body.path("hourly").get(0);
        assertEquals("2026-09-10T14:00", firstHour.path("recordedAt").asText());
        assertEquals(31.4, firstHour.path("temperature_2m").asDouble());
        assertEquals(35, firstHour.path("precipitation_probability").asInt());
        assertTrue(firstHour.path("relative_humidity_2m").isMissingNode());
        assertTrue(firstHour.path("precipitation").isMissingNode());
        assertTrue(firstHour.path("rain").isMissingNode());
        assertTrue(firstHour.path("cloud_cover").isMissingNode());
        assertTrue(firstHour.path("evapotranspiration").isMissingNode());
        assertTrue(firstHour.path("wind_speed_10m").isMissingNode());
        assertTrue(firstHour.path("wind_direction_10m").isMissingNode());
        assertEquals(7.4, firstHour.path("uv_index").asDouble());
        assertEquals(8.1, firstHour.path("uv_index_clear_sky").asDouble());
        assertEquals(2, firstHour.path("weather_code").asInt());
        assertEquals("Partly cloudy", firstHour.path("condition").asText());
        assertEquals(1, firstHour.path("is_day").asInt());

        JsonNode secondHour = body.path("hourly").get(1);
        assertEquals("2026-09-10T15:00", secondHour.path("recordedAt").asText());
        assertEquals("Slight or moderate thunderstorm",
                secondHour.path("condition").asText());
    }

    @Test
    void rejectsMismatchedHourlyArrays() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        String responseBody = successfulResponse().replace(
                "\"uv_index\": [7.4, 5.8]",
                "\"uv_index\": [7.4]");
        HourlyWeatherHandler handler = handler(requestedUri, responseBody);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "10.729",
                "longitude", "106.694")), null);

        assertEquals(502, response.getStatusCode());
        assertEquals("Unable to retrieve hourly weather data",
                JSON.readTree(response.getBody()).path("message").asText());
    }

    @Test
    void rejectsMalformedHourlyValues() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        String responseBody = successfulResponse().replace(
                "\"uv_index_clear_sky\": [8.1, 6.3]",
                "\"uv_index_clear_sky\": [8.1, null]");
        HourlyWeatherHandler handler = handler(requestedUri, responseBody);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "10.729",
                "longitude", "106.694")), null);

        assertEquals(502, response.getStatusCode());
        assertEquals("Unable to retrieve hourly weather data",
                JSON.readTree(response.getBody()).path("message").asText());
    }

    @Test
    void rejectsMalformedHourlyTimestamps() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        String responseBody = successfulResponse().replace(
                "\"time\": [\"2026-09-10T14:00\", \"2026-09-10T15:00\"]",
                "\"time\": [null, \"not-a-date\"]");
        HourlyWeatherHandler handler = handler(requestedUri, responseBody);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "10.729",
                "longitude", "106.694")), null);

        assertEquals(502, response.getStatusCode());
        assertEquals("Unable to retrieve hourly weather data",
                JSON.readTree(response.getBody()).path("message").asText());
    }

    @Test
    void rejectsInvalidCoordinatesBeforeCallingOpenMeteo() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        HourlyWeatherHandler handler = handler(requestedUri, successfulResponse());

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(Map.of(
                "latitude", "10.729",
                "longitude", "181")), null);

        assertEquals(400, response.getStatusCode());
        assertTrue(JSON.readTree(response.getBody()).path("message").asText()
                .contains("longitude must be between"));
        assertNull(requestedUri.get());
    }

    private static HourlyWeatherHandler handler(
            AtomicReference<URI> requestedUri,
            String responseBody) {
        return new HourlyWeatherHandler(
                uri -> {
                    requestedUri.set(uri);
                    return new OpenMeteoWeatherHandler.ProviderResponse(200, responseBody);
                },
                JSON,
                Clock.systemUTC(),
                "https://forecast.test");
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
                    "time": ["2026-09-10T14:00", "2026-09-10T15:00"],
                    "temperature_2m": [31.4, 30.2],
                    "precipitation_probability": [35, 68],
                    "weather_code": [2, 95],
                    "uv_index": [7.4, 5.8],
                    "uv_index_clear_sky": [8.1, 6.3],
                    "is_day": [1, 1]
                  }
                }
                """;
    }
}
