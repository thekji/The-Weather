package com.the.weather.lambda;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Open-Meteo handler for the hourly variables used by the forecast UI. */
public class HourlyWeatherHandler extends OpenMeteoWeatherHandler {

    private static final String DEFAULT_URL = "https://api.open-meteo.com/v1/forecast";

    /** Required public no-argument constructor for AWS Lambda. */
    public HourlyWeatherHandler() {
        super(
                environmentOrDefault(
                        "OPEN_METEO_HOURLY_URL",
                        environmentOrDefault("OPEN_METEO_FORECAST_URL", DEFAULT_URL)),
                WeatherDataType.HOURLY);
    }

    HourlyWeatherHandler(
            HttpTransport transport,
            ObjectMapper json,
            Clock clock,
            String openMeteoUrl) {
        super(transport, json, clock, openMeteoUrl, WeatherDataType.HOURLY);
    }
}
