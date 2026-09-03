package com.the.weather.lambda;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class WeatherHandler implements
        RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final List<String> WEATHER_FIELDS = List.of(
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "precipitation_probability",
            "precipitation",
            "rain",
            "is_day",
            "wind_speed_10m",
            "wind_direction_10m",
            "weather_code");

    private static final String OPEN_METEO_URL =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=10.8231"
                    + "&longitude=106.6297"
                    + "&current=" + String.join(",", WEATHER_FIELDS)
                    + "&timezone=Asia%2FHo_Chi_Minh";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public APIGatewayV2HTTPResponse handleRequest(
            APIGatewayV2HTTPEvent event,
            Context context) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(OPEN_METEO_URL))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "TheWeather/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return error(502, "Open-Meteo returned HTTP " + response.statusCode());
            }

            JsonNode current = JSON.readTree(response.body()).path("current");
            if (!current.isObject() || current.path("time").asText().isBlank()) {
                return error(502, "Open-Meteo returned an invalid response");
            }

            ObjectNode result = JSON.createObjectNode();
            result.put("recordedAt", current.path("time").asText());

            for (String field : WEATHER_FIELDS) {
                JsonNode value = current.get(field);
                if (value == null || !value.isNumber()) {
                    return error(502, "Open-Meteo response is missing " + field);
                }
                result.set(field, value);
            }

            return jsonResponse(200, result);
        } catch (HttpTimeoutException exception) {
            log(context, exception);
            return error(504, "Open-Meteo request timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log(context, exception);
            return error(500, "Weather request was interrupted");
        } catch (Exception exception) {
            log(context, exception);
            return error(502, "Unable to retrieve weather data");
        }
    }

    private static APIGatewayV2HTTPResponse error(int statusCode, String message) {
        ObjectNode body = JSON.createObjectNode();
        body.put("message", message);
        return jsonResponse(statusCode, body);
    }

    private static APIGatewayV2HTTPResponse jsonResponse(
            int statusCode,
            JsonNode body) {
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
}
