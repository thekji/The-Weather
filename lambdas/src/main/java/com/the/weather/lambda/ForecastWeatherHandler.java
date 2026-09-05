package com.the.weather.lambda;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Open-Meteo handler for current weather and forecasts up to 16 days ahead. */
public class ForecastWeatherHandler extends OpenMeteoWeatherHandler {

    private static final String DEFAULT_URL = "https://api.open-meteo.com/v1/forecast";

    /** Required public no-argument constructor for AWS Lambda. */
    public ForecastWeatherHandler() {
        super(environmentOrDefault("OPEN_METEO_FORECAST_URL", DEFAULT_URL),
                WeatherDataType.FORECAST);
    }

    ForecastWeatherHandler(
            HttpTransport transport,
            ObjectMapper json,
            Clock clock,
            String openMeteoUrl) {
        super(transport, json, clock, openMeteoUrl, WeatherDataType.FORECAST);
    }
}
