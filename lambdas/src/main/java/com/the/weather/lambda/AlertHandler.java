package com.the.weather.lambda;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;

/**
 * Scheduled entry point for the future weather-alert evaluation workflow.
 */
public class AlertHandler implements RequestHandler<ScheduledEvent, Map<String, String>> {

    @Override
    public Map<String, String> handleRequest(ScheduledEvent event, Context context) {
        String message = "Weather alerts are not implemented yet";
        log(context, message);
        return Map.of(
                "status", "NOT_IMPLEMENTED",
                "message", message);
    }

    private static void log(Context context, String message) {
        if (context != null) {
            context.getLogger().log(message);
        }
    }
}
