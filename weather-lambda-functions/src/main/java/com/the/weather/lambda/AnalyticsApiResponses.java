package com.the.weather.lambda;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared API Gateway HTTP API response helpers for analytics handlers. */
final class AnalyticsApiResponses {

    private static final Map<String, String> JSON_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Cache-Control", "no-store");

    private AnalyticsApiResponses() {}

    static APIGatewayV2HTTPResponse json(ObjectMapper json, int statusCode, Object body) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withHeaders(JSON_HEADERS)
                    .withBody(json.writeValueAsString(body))
                    .build();
        } catch (JsonProcessingException exception) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withHeaders(JSON_HEADERS)
                    .withBody("{\"message\":\"Unable to create analytics response.\"}")
                    .build();
        }
    }

    static APIGatewayV2HTTPResponse error(
            ObjectMapper json,
            int statusCode,
            String message) {
        return json(json, statusCode, Map.of("message", message));
    }
}
