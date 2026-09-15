package com.the.weather.feedback.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.the.weather.exception.ApiExceptionHandler;
import com.the.weather.feedback.dto.FeedbackRequest;
import com.the.weather.feedback.dto.FeedbackResponse;
import com.the.weather.feedback.model.FeedbackType;
import com.the.weather.feedback.service.FeedbackService;

class FeedbackControllerTests {

    private final FeedbackService service = mock(FeedbackService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FeedbackController(service))
                .setControllerAdvice(new ApiExceptionHandler(Clock.fixed(
                        Instant.parse("2026-09-14T08:30:00Z"), ZoneOffset.UTC)))
                .build();
    }

    @Test
    void putUsesAuthenticatedIdentityAndReturnsCurrentFeedback() throws Exception {
        when(service.set(eq("user_from_jwt"), eq("post_1"), any()))
                .thenReturn(new FeedbackResponse("post_1", FeedbackType.HELPFUL, 7, 1));

        mockMvc.perform(put("/api/posts/{postID}/feedback", "post_1")
                        .principal(() -> "user_from_jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feedbackType":"HELPFUL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postID").value("post_1"))
                .andExpect(jsonPath("$.myFeedback").value("HELPFUL"))
                .andExpect(jsonPath("$.helpfulCount").value(7))
                .andExpect(jsonPath("$.notHelpfulCount").value(1));

        ArgumentCaptor<FeedbackRequest> captor = ArgumentCaptor.forClass(FeedbackRequest.class);
        verify(service).set(eq("user_from_jwt"), eq("post_1"), captor.capture());
        assertThat(captor.getValue().feedbackType()).isEqualTo(FeedbackType.HELPFUL);
    }

    @Test
    void deleteUsesAuthenticatedIdentityAndReturnsNone() throws Exception {
        when(service.remove("user_from_jwt", "post_1"))
                .thenReturn(new FeedbackResponse("post_1", null, 6, 1));

        mockMvc.perform(delete("/api/posts/{postID}/feedback", "post_1")
                        .principal(() -> "user_from_jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myFeedback").doesNotExist())
                .andExpect(jsonPath("$.helpfulCount").value(6))
                .andExpect(jsonPath("$.notHelpfulCount").value(1));

        verify(service).remove("user_from_jwt", "post_1");
    }

    @Test
    void invalidFeedbackTypeReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/posts/{postID}/feedback", "post_1")
                        .principal(() -> "user_from_jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feedbackType":"MAYBE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(service, never()).set(any(), any(), any());
    }

    @Test
    void requestCannotSupplyAnotherUserID() throws Exception {
        mockMvc.perform(put("/api/posts/{postID}/feedback", "post_1")
                        .principal(() -> "user_from_jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "feedbackType":"HELPFUL",
                                  "userID":"attacker_selected_user"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(service, never()).set(any(), any(), any());
    }
}
