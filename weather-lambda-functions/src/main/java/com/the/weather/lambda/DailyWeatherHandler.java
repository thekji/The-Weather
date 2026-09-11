package com.the.weather.lambda;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Open-Meteo Lambda handler for the seven-day forecast used by the weather UI. */
public class DailyWeatherHandler extends OpenMeteoWeatherHandler {

    private static final String DEFAULT_URL = "https://api.open-meteo.com/v1/forecast";

    /** Required public no-argument constructor for AWS Lambda. */
    public DailyWeatherHandler() {
        super(environmentOrDefault(
                "OPEN_METEO_DAILY_URL",
                environmentOrDefault("OPEN_METEO_FORECAST_URL", DEFAULT_URL)),
                WeatherDataType.DAILY);
    }

    DailyWeatherHandler(
            HttpTransport transport,
            ObjectMapper json,
            Clock clock,
            String openMeteoUrl) {
        super(transport, json, clock, openMeteoUrl, WeatherDataType.DAILY);
    }
}
