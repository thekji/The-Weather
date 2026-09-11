package com.the.weather.weather.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.dto.DailyWeatherDto;
import com.the.weather.weather.dto.DailyWeatherEntryDto;
import com.the.weather.weather.dto.HourlyWeatherDto;
import com.the.weather.weather.dto.HourlyWeatherEntryDto;
import com.the.weather.weather.service.CurrentWeatherService;
import com.the.weather.weather.service.DailyWeatherService;
import com.the.weather.weather.service.HourlyWeatherService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("!local")
public class ApiGatewayWeatherClient implements CurrentWeatherService, HourlyWeatherService,
        DailyWeatherService {

    private final RestClient restClient;
    private final String forecastUrl;
    private final String hourlyUrl;
    private final String dailyUrl;

    @Autowired
    public ApiGatewayWeatherClient(
            @Value("${weather.forecast-url:}") String forecastUrl,
            @Value("${weather.hourly-url:}") String hourlyUrl,
            @Value("${weather.daily-url:}") String dailyUrl) {
        this(RestClient.builder(), forecastUrl, hourlyUrl, dailyUrl);
    }

    ApiGatewayWeatherClient(
            RestClient.Builder restClientBuilder,
            String forecastUrl) {
        this(restClientBuilder, forecastUrl, "", "");
    }

    ApiGatewayWeatherClient(
            RestClient.Builder restClientBuilder,
            String forecastUrl,
            String hourlyUrl) {
        this(restClientBuilder, forecastUrl, hourlyUrl, "");
    }

    ApiGatewayWeatherClient(
            RestClient.Builder restClientBuilder,
            String forecastUrl,
            String hourlyUrl,
            String dailyUrl) {
        this.restClient = restClientBuilder.build();
        this.forecastUrl = forecastUrl;
        this.hourlyUrl = hourlyUrl;
        this.dailyUrl = dailyUrl;
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
                    response.cloudCover(),
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

    @Override
    public HourlyWeatherDto hourly(double latitude, double longitude) {
        if (!StringUtils.hasText(hourlyUrl)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Hourly weather API Gateway is not configured");
        }

        try {
            OpenMeteoHourlyResponse response = restClient.get()
                    .uri(weatherUri(hourlyUrl, latitude, longitude))
                    .retrieve()
                    .body(OpenMeteoHourlyResponse.class);
            if (!isValid(response)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Hourly weather Lambda returned an invalid response");
            }

            List<HourlyWeatherEntryDto> hours = response.hourly().stream()
                    .map(hour -> new HourlyWeatherEntryDto(
                            hour.recordedAt(),
                            hour.temperature(),
                            hour.precipitationProbability(),
                            hour.weatherCode(),
                            hour.condition(),
                            hour.uvIndex(),
                            hour.uvIndexClearSky(),
                            hour.isDay() == 1))
                    .toList();

            return new HourlyWeatherDto(
                    response.latitude(),
                    response.longitude(),
                    response.timezone(),
                    hours);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Hourly weather service is temporarily unavailable",
                    exception);
        }
    }

    @Override
    public DailyWeatherDto daily(double latitude, double longitude) {
        if (!StringUtils.hasText(dailyUrl)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Daily weather API Gateway is not configured");
        }

        try {
            OpenMeteoDailyResponse response = restClient.get()
                    .uri(weatherUri(dailyUrl, latitude, longitude))
                    .retrieve()
                    .body(OpenMeteoDailyResponse.class);
            if (!isValid(response)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Daily weather Lambda returned an invalid response");
            }

            List<DailyWeatherEntryDto> days = response.daily().stream()
                    .map(day -> new DailyWeatherEntryDto(
                            day.date(),
                            day.maximumTemperature(),
                            day.minimumTemperature(),
                            day.sunrise(),
                            day.sunset(),
                            day.precipitationProbabilityMax(),
                            day.weatherCode(),
                            day.condition()))
                    .toList();
            return new DailyWeatherDto(
                    response.latitude(),
                    response.longitude(),
                    response.timezone(),
                    days);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Daily weather service is temporarily unavailable",
                    exception);
        }
    }

    private static String weatherUri(String baseUrl, double latitude, double longitude) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator
                + "latitude=" + latitude
                + "&longitude=" + longitude
                + "&timezone=auto";
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
                && response.cloudCover() != null
                && response.windSpeed() != null
                && response.windDirection() != null
                && response.weatherCode() != null
                && response.isDay() != null
                && StringUtils.hasText(response.recordedAt())
                && StringUtils.hasText(response.timezone())
                && StringUtils.hasText(response.condition());
    }

    private static boolean isValid(OpenMeteoHourlyResponse response) {
        return response != null
                && response.latitude() != null
                && response.longitude() != null
                && StringUtils.hasText(response.timezone())
                && response.hourly() != null
                && !response.hourly().isEmpty()
                && response.hourly().stream().allMatch(ApiGatewayWeatherClient::isValid);
    }

    private static boolean isValid(OpenMeteoHourlyEntry response) {
        return response != null
                && isIsoLocalDateTime(response.recordedAt())
                && response.temperature() != null
                && response.precipitationProbability() != null
                && response.weatherCode() != null
                && StringUtils.hasText(response.condition())
                && response.uvIndex() != null
                && response.uvIndexClearSky() != null
                && response.isDay() != null;
    }

    private static boolean isValid(OpenMeteoDailyResponse response) {
        return response != null
                && response.latitude() != null
                && response.longitude() != null
                && StringUtils.hasText(response.timezone())
                && response.daily() != null
                && !response.daily().isEmpty()
                && response.daily().stream().allMatch(ApiGatewayWeatherClient::isValid);
    }

    private static boolean isValid(OpenMeteoDailyEntry response) {
        return response != null
                && isIsoLocalDate(response.date())
                && response.maximumTemperature() != null
                && response.minimumTemperature() != null
                && isIsoLocalDateTime(response.sunrise())
                && isIsoLocalDateTime(response.sunset())
                && response.precipitationProbabilityMax() != null
                && response.weatherCode() != null
                && StringUtils.hasText(response.condition());
    }

    private static boolean isIsoLocalDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }

        try {
            LocalDateTime.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static boolean isIsoLocalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }

        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
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
            @JsonProperty("cloud_cover") Double cloudCover,
            @JsonProperty("wind_speed_10m") Double windSpeed,
            @JsonProperty("wind_direction_10m") Double windDirection,
            @JsonProperty("weather_code") Integer weatherCode,
            String condition,
            @JsonProperty("is_day") Integer isDay) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoHourlyResponse(
            Double latitude,
            Double longitude,
            String timezone,
            List<OpenMeteoHourlyEntry> hourly) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoHourlyEntry(
            String recordedAt,
            @JsonProperty("temperature_2m") Double temperature,
            @JsonProperty("precipitation_probability") Double precipitationProbability,
            @JsonProperty("weather_code") Integer weatherCode,
            String condition,
            @JsonProperty("uv_index") Double uvIndex,
            @JsonProperty("uv_index_clear_sky") Double uvIndexClearSky,
            @JsonProperty("is_day") Integer isDay) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoDailyResponse(
            Double latitude,
            Double longitude,
            String timezone,
            List<OpenMeteoDailyEntry> daily) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoDailyEntry(
            String date,
            @JsonProperty("temperature_2m_max") Double maximumTemperature,
            @JsonProperty("temperature_2m_min") Double minimumTemperature,
            String sunrise,
            String sunset,
            @JsonProperty("precipitation_probability_max")
            Double precipitationProbabilityMax,
            @JsonProperty("weather_code") Integer weatherCode,
            String condition) {
    }
}
