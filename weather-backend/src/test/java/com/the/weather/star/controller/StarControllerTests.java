package com.the.weather.star.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.the.weather.exception.ApiExceptionHandler;
import com.the.weather.star.dto.CreateStarRequest;
import com.the.weather.star.dto.StarredLocationResponse;
import com.the.weather.star.service.StarService;
import com.the.weather.star.service.StarService.CreateStarResult;

class StarControllerTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-08T01:00:00Z"),
            ZoneOffset.UTC);
    private static final StarredLocationResponse RESPONSE = new StarredLocationResponse(
            "geoapify:abc123",
            "RMIT University",
            "702 Nguyen Van Linh, Ho Chi Minh City",
            10.729,
            106.694,
            CLOCK.instant());

    private final StubStarService service = new StubStarService();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service.listResult = List.of();
        service.createResult = new CreateStarResult(RESPONSE, true);
        service.listUserID = null;
        service.listQuery = null;
        service.createUserID = null;
        service.createRequest = null;
        service.deleteUserID = null;
        service.deleteLocationID = null;
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StarController(service))
                .setControllerAdvice(new ApiExceptionHandler(CLOCK))
                .build();
    }

    @Test
    void listUsesTheAuthenticatedIdentityAndDoesNotExposeUserID() throws Exception {
        service.listResult = List.of(RESPONSE);

        mockMvc.perform(get("/api/stars").principal(() -> "user_123"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].locationID").value("geoapify:abc123"))
                .andExpect(jsonPath("$[0].name").value("RMIT University"))
                .andExpect(jsonPath("$[0].address")
                        .value("702 Nguyen Van Linh, Ho Chi Minh City"))
                .andExpect(jsonPath("$[0].latitude").value(10.729))
                .andExpect(jsonPath("$[0].longitude").value(106.694))
                .andExpect(jsonPath("$[0].starredAt").value("2026-09-08T01:00:00Z"))
                .andExpect(jsonPath("$[0].weatherAlertsEnabled").doesNotExist())
                .andExpect(jsonPath("$[0].userID").doesNotExist());

        assertThat(service.listUserID).isEqualTo("user_123");
        assertThat(service.listQuery).isNull();
    }

    @Test
    void searchPassesTheQueryAndAuthenticatedIdentityToTheService() throws Exception {
        service.listResult = List.of(RESPONSE);

        mockMvc.perform(get("/api/stars")
                        .queryParam("query", "nguyen van linh")
                        .principal(() -> "user_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locationID").value("geoapify:abc123"));

        assertThat(service.listUserID).isEqualTo("user_123");
        assertThat(service.listQuery).isEqualTo("nguyen van linh");
    }

    @Test
    void emptyListReturnsOkWithAnEmptyArray() throws Exception {
        mockMvc.perform(get("/api/stars").principal(() -> "user_123"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void createReturnsCreatedAndUsesTheAuthenticatedIdentity() throws Exception {
        mockMvc.perform(post("/api/stars")
                        .principal(() -> "user_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationID").value("geoapify:abc123"))
                .andExpect(jsonPath("$.weatherAlertsEnabled").doesNotExist())
                .andExpect(jsonPath("$.userID").doesNotExist());

        assertThat(service.createUserID).isEqualTo("user_123");
        assertThat(service.createRequest.locationID()).isEqualTo("geoapify:abc123");
    }

    @Test
    void duplicateCreateReturnsTheExistingStarWithOk() throws Exception {
        service.createResult = new CreateStarResult(RESPONSE, false);

        mockMvc.perform(post("/api/stars")
                        .principal(() -> "user_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.starredAt").value("2026-09-08T01:00:00Z"));
    }

    @Test
    void rejectsMissingFieldsAndOutOfRangeCoordinates() throws Exception {
        mockMvc.perform(post("/api/stars")
                        .principal(() -> "user_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationID": " ",
                                  "name": "RMIT University",
                                  "address": "Address",
                                  "longitude": 106.694
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/stars")
                        .principal(() -> "user_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationID": "geoapify:abc123",
                                  "name": "RMIT University",
                                  "address": "Address",
                                  "latitude": -90.01,
                                  "longitude": 180.01
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(service.createRequest).isNull();
    }

    @Test
    void rejectsClientControlledAndUnknownFieldsIncludingRetiredAlertField() throws Exception {
        mockMvc.perform(post("/api/stars")
                        .principal(() -> "user_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationID": "geoapify:abc123",
                                  "name": "RMIT University",
                                  "address": "Address",
                                  "latitude": 10.729,
                                  "longitude": 106.694,
                                  "userID": "user_attacker",
                                  "starredAt": "2000-01-01T00:00:00Z",
                                  "weatherAlertsEnabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(service.createRequest).isNull();
    }

    @Test
    void deleteUsesAuthenticatedUserIDAndLocationIDAndIsIdempotent() throws Exception {
        mockMvc.perform(delete("/api/stars/{locationID}", "geoapify:abc123")
                        .principal(() -> "user_123"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(service.deleteUserID).isEqualTo("user_123");
        assertThat(service.deleteLocationID).isEqualTo("geoapify:abc123");
    }

    private static String validRequest() {
        return """
                {
                  "locationID": "geoapify:abc123",
                  "name": "RMIT University",
                  "address": "702 Nguyen Van Linh, Ho Chi Minh City",
                  "latitude": 10.729,
                  "longitude": 106.694
                }
                """;
    }

    private static final class StubStarService extends StarService {
        private List<StarredLocationResponse> listResult = List.of();
        private CreateStarResult createResult;
        private String listUserID;
        private String listQuery;
        private String createUserID;
        private CreateStarRequest createRequest;
        private String deleteUserID;
        private String deleteLocationID;

        private StubStarService() {
            super(null, CLOCK);
        }

        @Override
        public List<StarredLocationResponse> list(
                String authenticatedUserID,
                String query) {
            listUserID = authenticatedUserID;
            listQuery = query;
            return listResult;
        }

        @Override
        public CreateStarResult create(
                String authenticatedUserID,
                CreateStarRequest request) {
            createUserID = authenticatedUserID;
            createRequest = request;
            return createResult;
        }

        @Override
        public void delete(String authenticatedUserID, String locationID) {
            deleteUserID = authenticatedUserID;
            deleteLocationID = locationID;
        }
    }
}
