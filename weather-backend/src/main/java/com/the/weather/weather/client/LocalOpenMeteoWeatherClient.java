package com.the.weather.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.service.CurrentWeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Local-development adapter that replaces API Gateway and Lambda. */
@Service
@Profile("local")
public class LocalOpenMeteoWeatherClient implements CurrentWeatherService {

    private static final String CURRENT_FIELDS = String.join(",",
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "precipitation",
            "rain",
            "is_day",
            "wind_speed_10m",
            "wind_direction_10m",
            "weather_code");

    private final RestClient restClient;

    @Autowired
    public LocalOpenMeteoWeatherClient(
            @Value("${weather.open-meteo-url:https://api.open-meteo.com/v1/forecast}")
            String openMeteoUrl) {
        this(RestClient.builder(), openMeteoUrl);
    }

    public LocalOpenMeteoWeatherClient(
            RestClient.Builder restClientBuilder,
            String openMeteoUrl) {
        this.restClient = restClientBuilder.baseUrl(openMeteoUrl).build();
    }

    @Override
    public CurrentWeatherDto current(double latitude, double longitude) {
        try {
            OpenMeteoResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", CURRENT_FIELDS)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(OpenMeteoResponse.class);

            if (!isValid(response)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Open-Meteo returned an invalid response");
            }

            OpenMeteoCurrent current = response.current();
            return new CurrentWeatherDto(
                    latitude,
                    longitude,
                    current.time(),
                    response.timezone(),
                    current.temperature(),
                    current.apparentTemperature(),
                    current.relativeHumidity(),
                    current.precipitation(),
                    current.rain(),
                    current.windSpeed(),
                    current.windDirection(),
                    current.weatherCode(),
                    weatherCondition(current.weatherCode()),
                    current.isDay() == 1);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Open-Meteo is temporarily unavailable",
                    exception);
        }
    }

    private static boolean isValid(OpenMeteoResponse response) {
        if (response == null || !StringUtils.hasText(response.timezone())) {
            return false;
        }

        OpenMeteoCurrent current = response.current();
        return current != null
                && StringUtils.hasText(current.time())
                && current.temperature() != null
                && current.apparentTemperature() != null
                && current.relativeHumidity() != null
                && current.precipitation() != null
                && current.rain() != null
                && current.windSpeed() != null
                && current.windDirection() != null
                && current.weatherCode() != null
                && current.isDay() != null;
    }

    private static String weatherCondition(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 56, 57 -> "Freezing drizzle";
            case 61, 63, 65 -> "Rain";
            case 66, 67 -> "Freezing rain";
            case 71, 73, 75 -> "Snowfall";
            case 77 -> "Snow grains";
            case 80, 81, 82 -> "Rain showers";
            case 85, 86 -> "Snow showers";
            case 95 -> "Thunderstorm";
            case 96, 99 -> "Thunderstorm with hail";
            default -> "Unknown";
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoResponse(
            String timezone,
            OpenMeteoCurrent current) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoCurrent(
            String time,
            @JsonProperty("temperature_2m") Double temperature,
            @JsonProperty("apparent_temperature") Double apparentTemperature,
            @JsonProperty("relative_humidity_2m") Double relativeHumidity,
            Double precipitation,
            Double rain,
            @JsonProperty("wind_speed_10m") Double windSpeed,
            @JsonProperty("wind_direction_10m") Double windDirection,
            @JsonProperty("weather_code") Integer weatherCode,
            @JsonProperty("is_day") Integer isDay) {
    }
}
