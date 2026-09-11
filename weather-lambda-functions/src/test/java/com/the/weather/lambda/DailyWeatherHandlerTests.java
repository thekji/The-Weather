package com.the.weather.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class DailyWeatherHandlerTests {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void returnsSevenDayForecastVariables() throws Exception {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        DailyWeatherHandler handler = new DailyWeatherHandler(
                uri -> {
                    requestedUri.set(uri);
                    return new OpenMeteoWeatherHandler.ProviderResponse(200, """
                            {
                              "timezone": "Asia/Ho_Chi_Minh",
                              "daily": {
                                "time": ["2026-09-11"],
                                "temperature_2m_max": [32.4],
                                "temperature_2m_min": [25.1],
                                "sunrise": ["2026-09-11T05:42"],
                                "sunset": ["2026-09-11T18:02"],
                                "precipitation_probability_max": [70],
                                "weather_code": [61]
                              }
                            }
                            """);
                },
                JSON,
                Clock.systemUTC(),
                "https://forecast.test");

        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setQueryStringParameters(Map.of(
                "latitude", "10.729",
                "longitude", "106.694",
                "timezone", "Asia/Ho_Chi_Minh"));
        APIGatewayV2HTTPResponse response = handler.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        String uri = requestedUri.get().toString();
        assertTrue(uri.contains("forecast_days=7"));
        assertTrue(uri.contains("temperature_2m_max"));
        assertTrue(uri.contains("precipitation_probability_max"));

        JsonNode day = JSON.readTree(response.getBody()).path("daily").get(0);
        assertEquals("2026-09-11", day.path("date").asText());
        assertEquals(32.4, day.path("temperature_2m_max").asDouble());
        assertEquals(25.1, day.path("temperature_2m_min").asDouble());
        assertEquals("2026-09-11T05:42", day.path("sunrise").asText());
        assertEquals("2026-09-11T18:02", day.path("sunset").asText());
        assertEquals(70, day.path("precipitation_probability_max").asInt());
        assertEquals("Slight rain", day.path("condition").asText());
    }

    @Test
    void rejectsMismatchedDailyArrays() throws Exception {
        String responseBody = successfulResponse().replace(
                "\"weather_code\": [61]",
                "\"weather_code\": []");
        DailyWeatherHandler handler = handler(responseBody);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(), null);

        assertEquals(502, response.getStatusCode());
        assertEquals("Unable to retrieve weather data",
                JSON.readTree(response.getBody()).path("message").asText());
    }

    @Test
    void rejectsInvalidDailyTimestamps() throws Exception {
        String responseBody = successfulResponse().replace(
                "\"sunrise\": [\"2026-09-11T05:42\"]",
                "\"sunrise\": [\"not-a-date\"]");
        DailyWeatherHandler handler = handler(responseBody);

        APIGatewayV2HTTPResponse response = handler.handleRequest(event(), null);

        assertEquals(502, response.getStatusCode());
        assertEquals("Unable to retrieve weather data",
                JSON.readTree(response.getBody()).path("message").asText());
    }

    private static DailyWeatherHandler handler(String responseBody) {
        return new DailyWeatherHandler(
                uri -> new OpenMeteoWeatherHandler.ProviderResponse(200, responseBody),
                JSON,
                Clock.systemUTC(),
                "https://forecast.test");
    }

    private static APIGatewayV2HTTPEvent event() {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setQueryStringParameters(Map.of(
                "latitude", "10.729",
                "longitude", "106.694"));
        return event;
    }

    private static String successfulResponse() {
        return """
                {
                  "timezone": "Asia/Ho_Chi_Minh",
                  "daily": {
                    "time": ["2026-09-11"],
                    "temperature_2m_max": [32.4],
                    "temperature_2m_min": [25.1],
                    "sunrise": ["2026-09-11T05:42"],
                    "sunset": ["2026-09-11T18:02"],
                    "precipitation_probability_max": [70],
                    "weather_code": [61]
                  }
                }
                """;
    }
}
