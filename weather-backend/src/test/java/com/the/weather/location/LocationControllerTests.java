package com.the.weather.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
                        "RMIT University Vietnam",
                        "702 Nguyen Van Linh, District 7, Ho Chi Minh City, Vietnam",
                        10.729,
                        106.694));

        mockMvc.perform(get("/api/locations/search").param("q", "  RMIT University  "))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        [{
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

    private static final class StubLocationSearchService implements LocationSearchService {
        private String query;
        private List<LocationSearchResultDto> results = List.of();

        @Override
        public List<LocationSearchResultDto> search(String query) {
            this.query = query;
            return results;
        }
    }
}
