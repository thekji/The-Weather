package com.the.weather.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
                          "wind_speed_10m": 8.7,
                          "wind_direction_10m": 185,
                          "weather_code": 2,
                          "condition": "Partly cloudy",
                          "is_day": 1
                        }
                        """, MediaType.APPLICATION_JSON));

        CurrentWeatherDto result = client.current(10.729, 106.694);

        assertThat(result.temperatureCelsius()).isEqualTo(31.4);
        assertThat(result.condition()).isEqualTo("Partly cloudy");
        assertThat(result.daytime()).isTrue();
        server.verify();
    }
}
