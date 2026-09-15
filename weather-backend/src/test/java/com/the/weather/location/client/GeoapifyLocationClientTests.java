package com.the.weather.location.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import com.the.weather.location.dto.LocationSearchResultDto;

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
                .andExpect(queryParam("limit", "20"))
                .andExpect(queryParam("apiKey", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "place_id": "51f1a7b3c9",
                              "result_type": "amenity",
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
                "geoapify:51f1a7b3c9",
                "RMIT University Vietnam",
                "702 Nguyen Van Linh, District 7, Ho Chi Minh City, Vietnam",
                10.729,
                106.694));
        server.verify();
    }

    @Test
    void searchCreatesAStableFallbackIdWhenPlaceIdIsMissing() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeoapifyLocationClient client = new GeoapifyLocationClient(
                builder,
                "https://api.geoapify.test",
                "test-key");

        server.expect(twice(), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.geoapify.test/v1/geocode/search?")))
                .andRespond(withSuccess("""
                        {
                          "results": [{
                            "result_type": "building",
                            "formatted": "10 Example Street, Melbourne VIC, Australia",
                            "address_line1": "10 Example Street",
                            "address_line2": "Melbourne VIC, Australia",
                            "lon": 144.9631,
                            "lat": -37.8076
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        LocationSearchResultDto first = client.search("10 Example Street").getFirst();
        LocationSearchResultDto second = client.search("10 Example Street").getFirst();

        assertThat(first.locationID())
                .startsWith("fallback:")
                .hasSize("fallback:".length() + 64)
                .isEqualTo(second.locationID());
        assertThat(first.name()).isEqualTo("10 Example Street");
        server.verify();
    }

    @Test
    void reverseNormalizesTheNearestGeoapifyResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeoapifyLocationClient client = new GeoapifyLocationClient(
                builder,
                "https://api.geoapify.test",
                "test-key");

        server.expect(once(), requestTo(org.hamcrest.Matchers.startsWith(
                        "https://api.geoapify.test/v1/geocode/reverse?")))
                .andExpect(queryParam("lat", "-37.8102"))
                .andExpect(queryParam("lon", "144.9633"))
                .andExpect(queryParam("format", "json"))
                .andExpect(queryParam("limit", "1"))
                .andExpect(queryParam("apiKey", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "results": [{
                            "place_id": "building-123",
                            "result_type": "building",
                            "formatted": "15 Example Street, Melbourne VIC 3000, Australia",
                            "address_line1": "15 Example Street",
                            "address_line2": "Melbourne VIC 3000, Australia",
                            "lon": 144.9632,
                            "lat": -37.8101
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        LocationSearchResultDto result = client.reverse(-37.8102, 144.9633);

        assertThat(result).isEqualTo(new LocationSearchResultDto(
                "geoapify:building-123",
                "15 Example Street",
                "15 Example Street, Melbourne VIC 3000, Australia",
                -37.8101,
                144.9632));
        server.verify();
    }
}
