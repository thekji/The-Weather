package com.the.weather.lambda;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Open-Meteo archive Lambda handler for historical weather from 1940. */
public class HistoricalWeatherHandler extends OpenMeteoWeatherHandler {

    private static final String DEFAULT_URL =
            "https://archive-api.open-meteo.com/v1/archive";

    /** Required public no-argument constructor for AWS Lambda. */
    public HistoricalWeatherHandler() {
        super(environmentOrDefault("OPEN_METEO_ARCHIVE_URL", DEFAULT_URL),
                WeatherDataType.HISTORICAL);
    }

    HistoricalWeatherHandler(
            HttpTransport transport,
            ObjectMapper json,
            Clock clock,
            String openMeteoUrl) {
        super(transport, json, clock, openMeteoUrl, WeatherDataType.HISTORICAL);
    }
}
