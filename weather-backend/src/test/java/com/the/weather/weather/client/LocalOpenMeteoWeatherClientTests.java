package com.the.weather.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.dto.DailyWeatherDto;
import com.the.weather.weather.dto.HourlyWeatherDto;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LocalOpenMeteoWeatherClientTests {

    @Test
    void mapsEveryWmoWeatherCodeToItsSpecificCondition() {
        Map<Integer, String> expectedConditions = Map.ofEntries(
                Map.entry(0, "Clear sky"),
                Map.entry(1, "Mainly clear"),
                Map.entry(2, "Partly cloudy"),
                Map.entry(3, "Overcast"),
                Map.entry(45, "Fog"),
                Map.entry(48, "Depositing rime fog"),
                Map.entry(51, "Light drizzle"),
                Map.entry(53, "Moderate drizzle"),
                Map.entry(55, "Dense drizzle"),
                Map.entry(56, "Light freezing drizzle"),
                Map.entry(57, "Dense freezing drizzle"),
                Map.entry(61, "Slight rain"),
                Map.entry(63, "Moderate rain"),
                Map.entry(65, "Heavy rain"),
                Map.entry(66, "Light freezing rain"),
                Map.entry(67, "Heavy freezing rain"),
                Map.entry(71, "Slight snowfall"),
                Map.entry(73, "Moderate snowfall"),
                Map.entry(75, "Heavy snowfall"),
                Map.entry(77, "Snow grains"),
                Map.entry(80, "Slight rain showers"),
                Map.entry(81, "Moderate rain showers"),
                Map.entry(82, "Violent rain showers"),
                Map.entry(85, "Slight snow showers"),
                Map.entry(86, "Heavy snow showers"),
                Map.entry(95, "Slight or moderate thunderstorm"),
                Map.entry(96, "Thunderstorm with slight hail"),
                Map.entry(99, "Thunderstorm with heavy hail"));

        expectedConditions.forEach((code, condition) ->
                assertThat(LocalOpenMeteoWeatherClient.weatherCondition(code))
                        .as("WMO code %s", code)
                        .isEqualTo(condition));
        assertThat(LocalOpenMeteoWeatherClient.weatherCondition(-1)).isEqualTo("Unknown");
    }

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
                .andExpect(queryParam("current", org.hamcrest.Matchers.containsString("cloud_cover")))
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
                            "cloud_cover": 58,
                            "wind_speed_10m": 8.7,
                            "wind_direction_10m": 185,
                            "weather_code": 2,
                            "is_day": 1
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        CurrentWeatherDto result = client.current(10.8167568, 106.6638006);

        assertThat(result.temperatureCelsius()).isEqualTo(31.4);
        assertThat(result.cloudCoverPercent()).isEqualTo(58);
        assertThat(result.condition()).isEqualTo("Partly cloudy");
        assertThat(result.timezone()).isEqualTo("Asia/Bangkok");
        assertThat(result.daytime()).isTrue();
        server.verify();
    }

    @Test
    void hourlyMapsEveryRequestedOpenMeteoVariable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalOpenMeteoWeatherClient client = new LocalOpenMeteoWeatherClient(
                builder,
                "https://open-meteo.test/v1/forecast");

        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://open-meteo.test/v1/forecast?")))
                .andExpect(queryParam("latitude", "10.8167568"))
                .andExpect(queryParam("longitude", "106.6638006"))
                .andExpect(queryParam("hourly", org.hamcrest.Matchers.containsString(
                        "precipitation_probability")))
                .andExpect(queryParam("hourly", org.hamcrest.Matchers.containsString(
                        "uv_index")))
                .andExpect(queryParam("hourly", org.hamcrest.Matchers.containsString(
                        "uv_index_clear_sky")))
                .andExpect(queryParam("forecast_days", "1"))
                .andExpect(queryParam("timezone", "auto"))
                .andRespond(withSuccess("""
                        {
                          "timezone": "Asia/Bangkok",
                          "hourly": {
                            "time": ["2026-09-10T15:00", "2026-09-10T16:00"],
                            "temperature_2m": [31.4, 30.8],
                            "precipitation_probability": [25, 35],
                            "weather_code": [2, 80],
                            "uv_index": [4.2, 2.5],
                            "uv_index_clear_sky": [5.1, 3.2],
                            "is_day": [1, 1]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        HourlyWeatherDto result = client.hourly(10.8167568, 106.6638006);

        assertThat(result.timezone()).isEqualTo("Asia/Bangkok");
        assertThat(result.hours()).hasSize(2);
        assertThat(result.hours().getFirst().time()).isEqualTo("2026-09-10T15:00");
        assertThat(result.hours().getFirst().temperatureCelsius()).isEqualTo(31.4);
        assertThat(result.hours().getFirst().precipitationProbabilityPercent()).isEqualTo(25);
        assertThat(result.hours().getFirst().weatherCode()).isEqualTo(2);
        assertThat(result.hours().getFirst().uvIndex()).isEqualTo(4.2);
        assertThat(result.hours().getFirst().uvIndexClearSky()).isEqualTo(5.1);
        assertThat(result.hours().getFirst().condition()).isEqualTo("Partly cloudy");
        assertThat(result.hours().getFirst().daytime()).isTrue();
        server.verify();
    }

    @Test
    void dailyMapsEveryRequestedOpenMeteoVariable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LocalOpenMeteoWeatherClient client = new LocalOpenMeteoWeatherClient(
                builder,
                "https://open-meteo.test/v1/forecast");

        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://open-meteo.test/v1/forecast?")))
                .andExpect(queryParam("daily", org.hamcrest.Matchers.containsString(
                        "temperature_2m_max")))
                .andExpect(queryParam("daily", org.hamcrest.Matchers.containsString(
                        "precipitation_probability_max")))
                .andExpect(queryParam("forecast_days", "7"))
                .andExpect(queryParam("timezone", "auto"))
                .andRespond(withSuccess("""
                        {
                          "timezone": "Asia/Ho_Chi_Minh",
                          "daily": {
                            "time": ["2026-09-11"],
                            "temperature_2m_max": [32.4],
                            "temperature_2m_min": [25.1],
                            "sunrise": ["2026-09-11T05:42"],
                            "sunset": ["2026-09-11T18:02"],
                            "precipitation_probability_max": [70],
                            "weather_code": [61]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        DailyWeatherDto result = client.daily(10.729, 106.694);

        assertThat(result.days()).hasSize(1);
        assertThat(result.days().getFirst().maximumTemperatureCelsius()).isEqualTo(32.4);
        assertThat(result.days().getFirst().minimumTemperatureCelsius()).isEqualTo(25.1);
        assertThat(result.days().getFirst().condition()).isEqualTo("Slight rain");
        server.verify();
    }
}
