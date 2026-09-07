package com.the.weather.weather.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.service.CurrentWeatherService;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WeatherControllerTests {

    private final StubCurrentWeatherService service = new StubCurrentWeatherService();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new WeatherController(service))
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

    private static final class StubCurrentWeatherService implements CurrentWeatherService {
        private CurrentWeatherDto result;
        private double latitude;
        private double longitude;
        private boolean called;

        @Override
        public CurrentWeatherDto current(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.called = true;
            return result;
        }
    }
}
