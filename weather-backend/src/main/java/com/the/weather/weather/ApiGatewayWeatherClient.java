package com.the.weather.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApiGatewayWeatherClient implements CurrentWeatherService {

    private final RestClient restClient;
    private final String forecastUrl;

    @Autowired
    public ApiGatewayWeatherClient(
            @Value("${weather.forecast-url:}") String forecastUrl) {
        this(RestClient.builder(), forecastUrl);
    }

    ApiGatewayWeatherClient(
            RestClient.Builder restClientBuilder,
            String forecastUrl) {
        this.restClient = restClientBuilder.build();
        this.forecastUrl = forecastUrl;
    }

    @Override
    public CurrentWeatherDto current(double latitude, double longitude) {
        if (!StringUtils.hasText(forecastUrl)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Weather API Gateway is not configured");
        }

        String separator = forecastUrl.contains("?") ? "&" : "?";
        String uri = forecastUrl + separator
                + "latitude=" + latitude
                + "&longitude=" + longitude
                + "&timezone=auto";

        try {
            OpenMeteoCurrentResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(OpenMeteoCurrentResponse.class);
            if (!isValid(response)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Weather Lambda returned an invalid response");
            }

            return new CurrentWeatherDto(
                    response.latitude(),
                    response.longitude(),
                    response.recordedAt(),
                    response.timezone(),
                    response.temperature(),
                    response.apparentTemperature(),
                    response.relativeHumidity(),
                    response.precipitation(),
                    response.rain(),
                    response.windSpeed(),
                    response.windDirection(),
                    response.weatherCode(),
                    response.condition(),
                    response.isDay() == 1);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Weather service is temporarily unavailable",
                    exception);
        }
    }

    private static boolean isValid(OpenMeteoCurrentResponse response) {
        return response != null
                && response.latitude() != null
                && response.longitude() != null
                && response.temperature() != null
                && response.apparentTemperature() != null
                && response.relativeHumidity() != null
                && response.precipitation() != null
                && response.rain() != null
                && response.windSpeed() != null
                && response.windDirection() != null
                && response.weatherCode() != null
                && response.isDay() != null
                && StringUtils.hasText(response.recordedAt())
                && StringUtils.hasText(response.timezone())
                && StringUtils.hasText(response.condition());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoCurrentResponse(
            Double latitude,
            Double longitude,
            String recordedAt,
            String timezone,
            @JsonProperty("temperature_2m") Double temperature,
            @JsonProperty("apparent_temperature") Double apparentTemperature,
            @JsonProperty("relative_humidity_2m") Double relativeHumidity,
            Double precipitation,
            Double rain,
            @JsonProperty("wind_speed_10m") Double windSpeed,
            @JsonProperty("wind_direction_10m") Double windDirection,
            @JsonProperty("weather_code") Integer weatherCode,
            String condition,
            @JsonProperty("is_day") Integer isDay) {
    }
}
