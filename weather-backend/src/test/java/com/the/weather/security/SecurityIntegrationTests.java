package com.the.weather.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(SecurityIntegrationTests.ProtectedEndpointConfiguration.class)
class SecurityIntegrationTests {

    private static final String TEST_SECRET = "test-only-secret-with-more-than-32-bytes";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void validJwtAuthenticatesWithTheUserIDAndDoesNotCreateASession() throws Exception {
        String token = jwtService.generateToken("user_123");

        mockMvc.perform(get("/api/protected-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userID").value("user_123"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void missingJwtIsRejected() throws Exception {
        mockMvc.perform(get("/api/protected-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    @Test
    void starRoutesRejectAMissingJwt() throws Exception {
        mockMvc.perform(get("/api/stars"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void starRoutesRejectAnInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/stars")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void jwtWithInvalidSignatureIsRejected() throws Exception {
        JwtService otherSigner = new JwtService(
                "different-test-secret-with-more-than-32-bytes",
                15,
                Clock.systemUTC());
        String token = otherSigner.generateToken("user_123");

        mockMvc.perform(get("/api/protected-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        Clock oldClock = Clock.fixed(
                Instant.now().minus(Duration.ofHours(1)),
                ZoneOffset.UTC);
        JwtService oldSigner = new JwtService(TEST_SECRET, 1, oldClock);
        String token = oldSigner.generateToken("user_123");

        mockMvc.perform(get("/api/protected-test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void existingPublicRouteStillAllowsRequestsWithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/system/ping"))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class ProtectedEndpointConfiguration {

        @Bean
        ProtectedTestController protectedTestController() {
            return new ProtectedTestController();
        }
    }

    @RestController
    static class ProtectedTestController {

        @GetMapping("/api/protected-test")
        Map<String, String> protectedEndpoint(Principal principal) {
            return Map.of("userID", principal.getName());
        }
    }
}
