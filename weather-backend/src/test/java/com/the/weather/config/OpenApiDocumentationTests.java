package com.the.weather.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class OpenApiDocumentationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void openApiDocumentIncludesPingEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TheWeather API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type")
                        .value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"))
                .andExpect(jsonPath("$.paths['/api/weather']").exists())
                .andExpect(jsonPath("$.paths['/api/weather/hourly']").exists())
                .andExpect(jsonPath("$.paths['/api/weather/daily']").exists())
                .andExpect(jsonPath("$.paths['/api/locations/search']").exists())
                .andExpect(jsonPath("$.paths['/api/stars'].get").exists())
                .andExpect(jsonPath("$.paths['/api/stars'].post").exists())
                .andExpect(jsonPath("$.paths['/api/stars/{locationID}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/system/ping']").exists());
    }

    @Test
    void swaggerUiEntryPointIsAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
