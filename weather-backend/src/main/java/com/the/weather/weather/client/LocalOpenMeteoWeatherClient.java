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
import java.util.ArrayList;
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

/** Local-development adapter that replaces API Gateway and Lambda. */
@Service
@Profile("local")
public class LocalOpenMeteoWeatherClient implements CurrentWeatherService, HourlyWeatherService,
        DailyWeatherService {

    private static final String CURRENT_FIELDS = String.join(",",
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "precipitation",
            "rain",
            "cloud_cover",
            "is_day",
            "wind_speed_10m",
            "wind_direction_10m",
            "weather_code");

    private static final String HOURLY_FIELDS = String.join(",",
            "temperature_2m",
            "precipitation_probability",
            "weather_code",
            "uv_index",
            "uv_index_clear_sky",
            "is_day");

    private static final String DAILY_FIELDS = String.join(",",
            "temperature_2m_max",
            "temperature_2m_min",
            "sunrise",
            "sunset",
            "precipitation_probability_max",
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
                    current.cloudCover(),
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

    @Override
    public HourlyWeatherDto hourly(double latitude, double longitude) {
        try {
            OpenMeteoHourlyResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("hourly", HOURLY_FIELDS)
                            .queryParam("forecast_days", 1)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(OpenMeteoHourlyResponse.class);

            if (!isValid(response)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Open-Meteo returned an invalid hourly response");
            }

            OpenMeteoHourly hourly = response.hourly();
            List<HourlyWeatherEntryDto> hours = new ArrayList<>(hourly.time().size());
            for (int index = 0; index < hourly.time().size(); index++) {
                int weatherCode = hourly.weatherCode().get(index);
                hours.add(new HourlyWeatherEntryDto(
                        hourly.time().get(index),
                        hourly.temperature().get(index),
                        hourly.precipitationProbability().get(index),
                        weatherCode,
                        weatherCondition(weatherCode),
                        hourly.uvIndex().get(index),
                        hourly.uvIndexClearSky().get(index),
                        hourly.isDay().get(index) == 1));
            }

            return new HourlyWeatherDto(
                    latitude,
                    longitude,
                    response.timezone(),
                    List.copyOf(hours));
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Open-Meteo is temporarily unavailable",
                    exception);
        }
    }

    @Override
    public DailyWeatherDto daily(double latitude, double longitude) {
        try {
            OpenMeteoDailyResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("daily", DAILY_FIELDS)
                            .queryParam("forecast_days", 7)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(OpenMeteoDailyResponse.class);

            if (!isValid(response)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Open-Meteo returned an invalid daily response");
            }

            OpenMeteoDaily daily = response.daily();
            List<DailyWeatherEntryDto> days = new ArrayList<>(daily.time().size());
            for (int index = 0; index < daily.time().size(); index++) {
                int weatherCode = daily.weatherCode().get(index);
                days.add(new DailyWeatherEntryDto(
                        daily.time().get(index),
                        daily.maximumTemperature().get(index),
                        daily.minimumTemperature().get(index),
                        daily.sunrise().get(index),
                        daily.sunset().get(index),
                        daily.precipitationProbabilityMax().get(index),
                        weatherCode,
                        weatherCondition(weatherCode)));
            }

            return new DailyWeatherDto(
                    latitude,
                    longitude,
                    response.timezone(),
                    List.copyOf(days));
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
                && current.cloudCover() != null
                && current.windSpeed() != null
                && current.windDirection() != null
                && current.weatherCode() != null
                && current.isDay() != null;
    }

    private static boolean isValid(OpenMeteoHourlyResponse response) {
        if (response == null || !StringUtils.hasText(response.timezone())) {
            return false;
        }

        OpenMeteoHourly hourly = response.hourly();
        if (hourly == null || hourly.time() == null || hourly.time().isEmpty()) {
            return false;
        }

        int size = hourly.time().size();
        return hourly.time().stream().allMatch(LocalOpenMeteoWeatherClient::isIsoLocalDateTime)
                && hasValues(hourly.temperature(), size)
                && hasValues(hourly.precipitationProbability(), size)
                && hasValues(hourly.weatherCode(), size)
                && hasValues(hourly.uvIndex(), size)
                && hasValues(hourly.uvIndexClearSky(), size)
                && hasValues(hourly.isDay(), size);
    }

    private static boolean isValid(OpenMeteoDailyResponse response) {
        if (response == null || !StringUtils.hasText(response.timezone())) {
            return false;
        }

        OpenMeteoDaily daily = response.daily();
        if (daily == null || daily.time() == null || daily.time().isEmpty()) {
            return false;
        }

        int size = daily.time().size();
        return daily.time().stream().allMatch(LocalOpenMeteoWeatherClient::isIsoLocalDate)
                && hasValues(daily.maximumTemperature(), size)
                && hasValues(daily.minimumTemperature(), size)
                && hasValues(daily.sunrise(), size)
                && hasValues(daily.sunset(), size)
                && hasValues(daily.precipitationProbabilityMax(), size)
                && hasValues(daily.weatherCode(), size)
                && daily.sunrise().stream().allMatch(
                        LocalOpenMeteoWeatherClient::isIsoLocalDateTime)
                && daily.sunset().stream().allMatch(
                        LocalOpenMeteoWeatherClient::isIsoLocalDateTime);
    }

    private static boolean hasValues(List<?> values, int expectedSize) {
        return values != null
                && values.size() == expectedSize
                && values.stream().allMatch(value -> value != null);
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

    static String weatherCondition(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45 -> "Fog";
            case 48 -> "Depositing rime fog";
            case 51 -> "Light drizzle";
            case 53 -> "Moderate drizzle";
            case 55 -> "Dense drizzle";
            case 56 -> "Light freezing drizzle";
            case 57 -> "Dense freezing drizzle";
            case 61 -> "Slight rain";
            case 63 -> "Moderate rain";
            case 65 -> "Heavy rain";
            case 66 -> "Light freezing rain";
            case 67 -> "Heavy freezing rain";
            case 71 -> "Slight snowfall";
            case 73 -> "Moderate snowfall";
            case 75 -> "Heavy snowfall";
            case 77 -> "Snow grains";
            case 80 -> "Slight rain showers";
            case 81 -> "Moderate rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Slight snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Slight or moderate thunderstorm";
            case 96 -> "Thunderstorm with slight hail";
            case 99 -> "Thunderstorm with heavy hail";
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
            @JsonProperty("cloud_cover") Double cloudCover,
            @JsonProperty("wind_speed_10m") Double windSpeed,
            @JsonProperty("wind_direction_10m") Double windDirection,
            @JsonProperty("weather_code") Integer weatherCode,
            @JsonProperty("is_day") Integer isDay) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoHourlyResponse(
            String timezone,
            OpenMeteoHourly hourly) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoHourly(
            List<String> time,
            @JsonProperty("temperature_2m") List<Double> temperature,
            @JsonProperty("precipitation_probability") List<Double> precipitationProbability,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("uv_index") List<Double> uvIndex,
            @JsonProperty("uv_index_clear_sky") List<Double> uvIndexClearSky,
            @JsonProperty("is_day") List<Integer> isDay) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoDailyResponse(
            String timezone,
            OpenMeteoDaily daily) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenMeteoDaily(
            List<String> time,
            @JsonProperty("temperature_2m_max") List<Double> maximumTemperature,
            @JsonProperty("temperature_2m_min") List<Double> minimumTemperature,
            List<String> sunrise,
            List<String> sunset,
            @JsonProperty("precipitation_probability_max")
            List<Double> precipitationProbabilityMax,
            @JsonProperty("weather_code") List<Integer> weatherCode) {
    }
}
