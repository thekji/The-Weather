package com.the.weather.map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MapConfigControllerTests {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MapConfigController("test-map-key"))
            .build();

    @Test
    void configReturnsOnlyTheBrowserMapKey() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "geoapifyMapApiKey": "test-map-key"
                        }
                        """));
    }
}
