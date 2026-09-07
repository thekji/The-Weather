package com.the.weather.location.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.the.weather.location.dto.LocationSearchResultDto;
import com.the.weather.location.service.LocationSearchService;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LocationControllerTests {

    private final StubLocationSearchService service = new StubLocationSearchService();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new LocationController(service))
            .build();

    @Test
    void searchReturnsAListOfNormalizedDtos() throws Exception {
        service.results = List.of(
                new LocationSearchResultDto(
                        "geoapify:51f1a7b3c9",
                        "RMIT University Vietnam",
                        "702 Nguyen Van Linh, District 7, Ho Chi Minh City, Vietnam",
                        10.729,
                        106.694));

        mockMvc.perform(get("/api/locations/search").param("q", "  RMIT University  "))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        [{
                          "locationID": "geoapify:51f1a7b3c9",
                          "name": "RMIT University Vietnam",
                          "address": "702 Nguyen Van Linh, District 7, Ho Chi Minh City, Vietnam",
                          "latitude": 10.729,
                          "longitude": 106.694
                        }]
                        """));

        assertThat(service.query).isEqualTo("RMIT University");
    }

    @Test
    void searchRejectsABlankQuery() throws Exception {
        mockMvc.perform(get("/api/locations/search").param("q", "   "))
                .andExpect(status().isBadRequest());

        assertThat(service.query).isNull();
    }

    @Test
    void reverseReturnsANormalizedLocation() throws Exception {
        service.reverseResult = new LocationSearchResultDto(
                "geoapify:building-123",
                "15 Example Street",
                "15 Example Street, Melbourne VIC 3000, Australia",
                -37.8101,
                144.9632);

        mockMvc.perform(get("/api/locations/reverse")
                        .param("latitude", "-37.8102")
                        .param("longitude", "144.9633"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "locationID": "geoapify:building-123",
                          "name": "15 Example Street",
                          "latitude": -37.8101,
                          "longitude": 144.9632
                        }
                        """));

        assertThat(service.reverseLatitude).isEqualTo(-37.8102);
        assertThat(service.reverseLongitude).isEqualTo(144.9633);
    }

    @Test
    void reverseRejectsInvalidCoordinates() throws Exception {
        mockMvc.perform(get("/api/locations/reverse")
                        .param("latitude", "91")
                        .param("longitude", "144.9633"))
                .andExpect(status().isBadRequest());

        assertThat(service.reverseResult).isNull();
    }

    private static final class StubLocationSearchService implements LocationSearchService {
        private String query;
        private List<LocationSearchResultDto> results = List.of();
        private LocationSearchResultDto reverseResult;
        private double reverseLatitude;
        private double reverseLongitude;

        @Override
        public List<LocationSearchResultDto> search(String query) {
            this.query = query;
            return results;
        }

        @Override
        public LocationSearchResultDto reverse(double latitude, double longitude) {
            this.reverseLatitude = latitude;
            this.reverseLongitude = longitude;
            return reverseResult;
        }
    }
}
