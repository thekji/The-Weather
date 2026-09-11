package com.the.weather.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.dto.DailyWeatherDto;
import com.the.weather.weather.dto.HourlyWeatherDto;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ApiGatewayWeatherClientTests {

    @Test
    void currentMapsTheForecastLambdaResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayWeatherClient client = new ApiGatewayWeatherClient(
                builder,
                "https://gateway.test/weather");

        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://gateway.test/weather?")))
                .andExpect(queryParam("latitude", "10.729"))
                .andExpect(queryParam("longitude", "106.694"))
                .andExpect(queryParam("timezone", "auto"))
                .andRespond(withSuccess("""
                        {
                          "latitude": 10.729,
                          "longitude": 106.694,
                          "recordedAt": "2026-09-06T14:15",
                          "timezone": "Asia/Ho_Chi_Minh",
                          "temperature_2m": 31.4,
                          "apparent_temperature": 35.1,
                          "relative_humidity_2m": 71,
                          "precipitation": 0.0,
                          "rain": 0.0,
                          "cloud_cover": 58,
                          "wind_speed_10m": 8.7,
                          "wind_direction_10m": 185,
                          "weather_code": 2,
                          "condition": "Partly cloudy",
                          "is_day": 1
                        }
                        """, MediaType.APPLICATION_JSON));

        CurrentWeatherDto result = client.current(10.729, 106.694);

        assertThat(result.temperatureCelsius()).isEqualTo(31.4);
        assertThat(result.cloudCoverPercent()).isEqualTo(58);
        assertThat(result.condition()).isEqualTo("Partly cloudy");
        assertThat(result.daytime()).isTrue();
        server.verify();
    }

    @Test
    void hourlyMapsTheHourlyLambdaResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayWeatherClient client = new ApiGatewayWeatherClient(
                builder,
                "https://gateway.test/weather",
                "https://gateway.test/weather/hourly");

        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://gateway.test/weather/hourly?")))
                .andExpect(queryParam("latitude", "10.729"))
                .andExpect(queryParam("longitude", "106.694"))
                .andExpect(queryParam("timezone", "auto"))
                .andRespond(withSuccess("""
                        {
                          "latitude": 10.729,
                          "longitude": 106.694,
                          "timezone": "Asia/Ho_Chi_Minh",
                          "hourly": [{
                            "recordedAt": "2026-09-10T15:00",
                            "temperature_2m": 31.4,
                            "precipitation_probability": 25,
                            "weather_code": 80,
                            "condition": "Slight rain showers",
                            "uv_index": 4.2,
                            "uv_index_clear_sky": 5.1,
                            "is_day": 1
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        HourlyWeatherDto result = client.hourly(10.729, 106.694);

        assertThat(result.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(result.hours()).hasSize(1);
        assertThat(result.hours().getFirst().time()).isEqualTo("2026-09-10T15:00");
        assertThat(result.hours().getFirst().temperatureCelsius()).isEqualTo(31.4);
        assertThat(result.hours().getFirst().precipitationProbabilityPercent()).isEqualTo(25);
        assertThat(result.hours().getFirst().weatherCode()).isEqualTo(80);
        assertThat(result.hours().getFirst().uvIndex()).isEqualTo(4.2);
        assertThat(result.hours().getFirst().uvIndexClearSky()).isEqualTo(5.1);
        assertThat(result.hours().getFirst().condition()).isEqualTo("Slight rain showers");
        assertThat(result.hours().getFirst().daytime()).isTrue();
        server.verify();
    }

    @Test
    void dailyMapsTheDailyLambdaResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiGatewayWeatherClient client = new ApiGatewayWeatherClient(
                builder,
                "https://gateway.test/weather",
                "https://gateway.test/weather/hourly",
                "https://gateway.test/weather/daily");

        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://gateway.test/weather/daily?")))
                .andExpect(queryParam("latitude", "10.729"))
                .andExpect(queryParam("longitude", "106.694"))
                .andExpect(queryParam("timezone", "auto"))
                .andRespond(withSuccess("""
                        {
                          "latitude": 10.729,
                          "longitude": 106.694,
                          "timezone": "Asia/Ho_Chi_Minh",
                          "daily": [{
                            "date": "2026-09-11",
                            "temperature_2m_max": 32.4,
                            "temperature_2m_min": 25.1,
                            "sunrise": "2026-09-11T05:42",
                            "sunset": "2026-09-11T18:02",
                            "precipitation_probability_max": 70,
                            "weather_code": 61,
                            "condition": "Slight rain"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        DailyWeatherDto result = client.daily(10.729, 106.694);

        assertThat(result.days()).hasSize(1);
        assertThat(result.days().getFirst().maximumTemperatureCelsius()).isEqualTo(32.4);
        assertThat(result.days().getFirst().minimumTemperatureCelsius()).isEqualTo(25.1);
        assertThat(result.days().getFirst().sunrise()).isEqualTo("2026-09-11T05:42");
        assertThat(result.days().getFirst().sunset()).isEqualTo("2026-09-11T18:02");
        assertThat(result.days().getFirst().precipitationProbabilityMaxPercent()).isEqualTo(70);
        server.verify();
    }
}
