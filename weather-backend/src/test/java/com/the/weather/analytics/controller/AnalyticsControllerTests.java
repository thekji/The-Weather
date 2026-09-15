package com.the.weather.analytics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.the.weather.analytics.dto.AnalyticsRefreshResponseDto;
import com.the.weather.analytics.dto.AnalyticsRefreshStatus;
import com.the.weather.analytics.dto.AnalyticsRefreshStatusDto;
import com.the.weather.analytics.dto.AnalyticsSummaryDto;
import com.the.weather.analytics.dto.LocationPostCountDto;
import com.the.weather.analytics.dto.RatingDistributionDto;
import com.the.weather.analytics.service.AnalyticsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnalyticsControllerTests {

    private final StubAnalyticsService service = new StubAnalyticsService();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service.refreshCalled = false;
        service.statusCalled = false;
        service.summaryCalled = false;
        service.refreshResult = new AnalyticsRefreshResponseDto(
                AnalyticsRefreshStatus.REFRESHING,
                "Latest community data exported and analytics catalogue refresh started.");
        service.statusResult = new AnalyticsRefreshStatusDto(AnalyticsRefreshStatus.READY);
        service.summaryResult = summary();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AnalyticsController(service))
                .build();
    }

    @Test
    void refreshReturnsAcceptedAndTheRefreshContract() throws Exception {
        mockMvc.perform(post("/api/analytics/refresh"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("REFRESHING"))
                .andExpect(jsonPath("$.message")
                        .value("Latest community data exported and analytics catalogue refresh started."));

        assertThat(service.refreshCalled).isTrue();
    }

    @Test
    void refreshStatusReturnsTheGatewayStatusContract() throws Exception {
        mockMvc.perform(get("/api/analytics/refresh/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"READY\"}"));

        assertThat(service.statusCalled).isTrue();
    }

    @Test
    void summaryReturnsAllFourAthenaMetrics() throws Exception {
        mockMvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalPosts").value(76))
                .andExpect(jsonPath("$.averageWeatherAccuracy").value(4.12))
                .andExpect(jsonPath("$.ratingDistribution.length()").value(5))
                .andExpect(jsonPath("$.ratingDistribution[0].rating").value(1))
                .andExpect(jsonPath("$.ratingDistribution[4].count").value(31))
                .andExpect(jsonPath("$.topLocations[0].locationName").value("Petrolimex"))
                .andExpect(jsonPath("$.topLocations[0].postCount").value(12))
                .andExpect(jsonPath("$.generatedAt").value("2026-09-15T08:45:00Z"));

        assertThat(service.summaryCalled).isTrue();
    }

    private static AnalyticsSummaryDto summary() {
        return new AnalyticsSummaryDto(
                76,
                4.12,
                List.of(
                        new RatingDistributionDto(1, 3),
                        new RatingDistributionDto(2, 5),
                        new RatingDistributionDto(3, 12),
                        new RatingDistributionDto(4, 25),
                        new RatingDistributionDto(5, 31)),
                List.of(
                        new LocationPostCountDto("Petrolimex", 12),
                        new LocationPostCountDto("RMIT", 9)),
                "2026-09-15T08:45:00Z");
    }

    private static final class StubAnalyticsService implements AnalyticsService {
        private AnalyticsRefreshResponseDto refreshResult;
        private AnalyticsRefreshStatusDto statusResult;
        private AnalyticsSummaryDto summaryResult;
        private boolean refreshCalled;
        private boolean statusCalled;
        private boolean summaryCalled;

        @Override
        public AnalyticsRefreshResponseDto refresh() {
            refreshCalled = true;
            return refreshResult;
        }

        @Override
        public AnalyticsRefreshStatusDto refreshStatus() {
            statusCalled = true;
            return statusResult;
        }

        @Override
        public AnalyticsSummaryDto summary() {
            summaryCalled = true;
            return summaryResult;
        }
    }
}
