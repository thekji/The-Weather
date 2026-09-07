package com.the.weather.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.the.weather.weather.dto.CurrentWeatherDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LocalOpenMeteoWeatherClientTests {

    @Test
    void currentMapsOpenMeteoResponseWithoutApiGateway() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalOpenMeteoWeatherClient client = new LocalOpenMeteoWeatherClient(
                builder,
                "https://open-meteo.test/v1/forecast");

        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://open-meteo.test/v1/forecast?")))
                .andExpect(queryParam("latitude", "10.8167568"))
                .andExpect(queryParam("longitude", "106.6638006"))
                .andExpect(queryParam("timezone", "auto"))
                .andRespond(withSuccess("""
                        {
                          "timezone": "Asia/Bangkok",
                          "current": {
                            "time": "2026-09-06T17:30",
                            "temperature_2m": 31.4,
                            "apparent_temperature": 35.1,
                            "relative_humidity_2m": 71,
                            "precipitation": 0.0,
                            "rain": 0.0,
                            "wind_speed_10m": 8.7,
                            "wind_direction_10m": 185,
                            "weather_code": 2,
                            "is_day": 1
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        CurrentWeatherDto result = client.current(10.8167568, 106.6638006);

        assertThat(result.temperatureCelsius()).isEqualTo(31.4);
        assertThat(result.condition()).isEqualTo("Partly cloudy");
        assertThat(result.timezone()).isEqualTo("Asia/Bangkok");
        assertThat(result.daytime()).isTrue();
        server.verify();
    }
}
