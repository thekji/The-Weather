package com.the.weather.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeoapifyLocationClientTests {

    @Test
    void searchNormalizesAndFiltersGeoapifyResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeoapifyLocationClient client = new GeoapifyLocationClient(
                builder,
                "https://api.geoapify.test",
                "test-key");

        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.geoapify.test/v1/geocode/search?")))
                .andExpect(queryParam("text", "RMIT%20Saigon%20South"))
                .andExpect(queryParam("format", "json"))
                .andExpect(queryParam("limit", "10"))
                .andExpect(queryParam("apiKey", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "name": "RMIT University Vietnam",
                              "formatted": "702 Nguyen Van Linh, District 7, Ho Chi Minh City, Vietnam",
                              "address_line1": "RMIT University Vietnam",
                              "address_line2": "District 7, Ho Chi Minh City, Vietnam",
                              "lon": 106.694,
                              "lat": 10.729
                            },
                            {
                              "formatted": "Invalid coordinate",
                              "lon": 300,
                              "lat": 10
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<LocationSearchResultDto> results = client.search("RMIT Saigon South");

        assertThat(results).containsExactly(new LocationSearchResultDto(
                "RMIT University Vietnam",
                "702 Nguyen Van Linh, District 7, Ho Chi Minh City, Vietnam",
                10.729,
                106.694));
        server.verify();
    }
}
