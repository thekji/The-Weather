package com.the.weather.weather.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.dto.DailyWeatherDto;
import com.the.weather.weather.dto.DailyWeatherEntryDto;
import com.the.weather.weather.dto.HourlyWeatherDto;
import com.the.weather.weather.dto.HourlyWeatherEntryDto;
import com.the.weather.weather.service.CurrentWeatherService;
import com.the.weather.weather.service.DailyWeatherService;
import com.the.weather.weather.service.HourlyWeatherService;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WeatherControllerTests {

    private final StubCurrentWeatherService service = new StubCurrentWeatherService();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new WeatherController(service, service, service))
            .build();

    @Test
    void currentWeatherReturnsNormalizedWeather() throws Exception {
        service.result = new CurrentWeatherDto(
                10.729,
                106.694,
                "2026-09-06T14:15",
                "Asia/Ho_Chi_Minh",
                31.4,
                35.1,
                71,
                0,
                0,
                58,
                8.7,
                185,
                2,
                "Partly cloudy",
                true);

        mockMvc.perform(get("/api/weather")
                        .param("latitude", "10.729")
                        .param("longitude", "106.694"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "latitude": 10.729,
                          "longitude": 106.694,
                          "recordedAt": "2026-09-06T14:15",
                          "timezone": "Asia/Ho_Chi_Minh",
                          "temperatureCelsius": 31.4,
                          "cloudCoverPercent": 58,
                          "condition": "Partly cloudy",
                          "daytime": true
                        }
                        """));

        assertThat(service.latitude).isEqualTo(10.729);
        assertThat(service.longitude).isEqualTo(106.694);
    }

    @Test
    void currentWeatherRejectsInvalidCoordinates() throws Exception {
        mockMvc.perform(get("/api/weather")
                        .param("latitude", "91")
                        .param("longitude", "106.694"))
                .andExpect(status().isBadRequest());

        assertThat(service.called).isFalse();
    }

    @Test
    void hourlyWeatherReturnsNormalizedHours() throws Exception {
        service.hourlyResult = new HourlyWeatherDto(
                10.729,
                106.694,
                "Asia/Ho_Chi_Minh",
                List.of(new HourlyWeatherEntryDto(
                        "2026-09-10T15:00",
                        31.4,
                        25,
                        80,
                        "Slight rain showers",
                        4.2,
                        5.1,
                        true)));

        mockMvc.perform(get("/api/weather/hourly")
                        .param("latitude", "10.729")
                        .param("longitude", "106.694"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "latitude": 10.729,
                          "longitude": 106.694,
                          "timezone": "Asia/Ho_Chi_Minh",
                          "hours": [{
                            "time": "2026-09-10T15:00",
                            "temperatureCelsius": 31.4,
                            "precipitationProbabilityPercent": 25.0,
                            "weatherCode": 80,
                            "condition": "Slight rain showers",
                            "uvIndex": 4.2,
                            "uvIndexClearSky": 5.1,
                            "daytime": true
                          }]
                        }
                        """));

        assertThat(service.hourlyLatitude).isEqualTo(10.729);
        assertThat(service.hourlyLongitude).isEqualTo(106.694);
    }

    @Test
    void dailyWeatherReturnsSevenDayVariables() throws Exception {
        service.dailyResult = new DailyWeatherDto(
                10.729,
                106.694,
                "Asia/Ho_Chi_Minh",
                List.of(new DailyWeatherEntryDto(
                        "2026-09-11",
                        32.4,
                        25.1,
                        "2026-09-11T05:42",
                        "2026-09-11T18:02",
                        70,
                        61,
                        "Slight rain")));

        mockMvc.perform(get("/api/weather/daily")
                        .param("latitude", "10.729")
                        .param("longitude", "106.694"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "timezone": "Asia/Ho_Chi_Minh",
                          "days": [{
                            "date": "2026-09-11",
                            "maximumTemperatureCelsius": 32.4,
                            "minimumTemperatureCelsius": 25.1,
                            "sunrise": "2026-09-11T05:42",
                            "sunset": "2026-09-11T18:02",
                            "precipitationProbabilityMaxPercent": 70.0
                          }]
                        }
                        """));
    }

    private static final class StubCurrentWeatherService
            implements CurrentWeatherService, HourlyWeatherService, DailyWeatherService {
        private CurrentWeatherDto result;
        private HourlyWeatherDto hourlyResult;
        private DailyWeatherDto dailyResult;
        private double latitude;
        private double longitude;
        private double hourlyLatitude;
        private double hourlyLongitude;
        private boolean called;

        @Override
        public CurrentWeatherDto current(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.called = true;
            return result;
        }

        @Override
        public HourlyWeatherDto hourly(double latitude, double longitude) {
            this.hourlyLatitude = latitude;
            this.hourlyLongitude = longitude;
            return hourlyResult;
        }

        @Override
        public DailyWeatherDto daily(double latitude, double longitude) {
            return dailyResult;
        }
    }
}
