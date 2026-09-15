package com.the.weather.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.the.weather.feedback.dto.FeedbackRequest;
import com.the.weather.feedback.model.FeedbackType;
import com.the.weather.feedback.model.PostFeedback;
import com.the.weather.feedback.repository.FeedbackRepository;
import com.the.weather.feedback.repository.FeedbackRepository.FeedbackStateResult;
import com.the.weather.post.exception.PostApiException;
import com.the.weather.post.model.ApiWeather;
import com.the.weather.post.model.CommunityPost;
import com.the.weather.post.repository.PostRepository;

class FeedbackServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-14T08:30:00Z");

    private final PostRepository posts = mock(PostRepository.class);
    private final FeedbackRepository feedback = mock(FeedbackRepository.class);
    private final FeedbackService service = new FeedbackService(
            posts, feedback, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(posts.findByID("post_1")).thenReturn(Optional.of(post("author_1")));
    }

    @Test
    void setHelpfulUsesAuthenticatedUserAndReturnsViewerState() {
        when(feedback.set(any())).thenReturn(
                new FeedbackStateResult(FeedbackType.HELPFUL, 7, 1));

        var response = service.set(
                "authenticated_user",
                "post_1",
                new FeedbackRequest(FeedbackType.HELPFUL));

        ArgumentCaptor<PostFeedback> captor = ArgumentCaptor.forClass(PostFeedback.class);
        verify(feedback).set(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PostFeedback(
                "post_1", "authenticated_user", FeedbackType.HELPFUL, NOW));
        assertThat(response.postID()).isEqualTo("post_1");
        assertThat(response.myFeedback()).isEqualTo(FeedbackType.HELPFUL);
        assertThat(response.helpfulCount()).isEqualTo(7);
        assertThat(response.notHelpfulCount()).isEqualTo(1);
    }

    @Test
    void setNotHelpfulSupportsSwitchingFeedback() {
        when(feedback.set(any())).thenReturn(
                new FeedbackStateResult(FeedbackType.NOT_HELPFUL, 6, 2));

        var response = service.set(
                "authenticated_user",
                "post_1",
                new FeedbackRequest(FeedbackType.NOT_HELPFUL));

        assertThat(response.myFeedback()).isEqualTo(FeedbackType.NOT_HELPFUL);
        assertThat(response.helpfulCount()).isEqualTo(6);
        assertThat(response.notHelpfulCount()).isEqualTo(2);
    }

    @Test
    void removeFeedbackReturnsNoneAndUpdatedCounts() {
        when(feedback.remove("post_1", "authenticated_user"))
                .thenReturn(new FeedbackStateResult(null, 6, 1));

        var response = service.remove("authenticated_user", "post_1");

        assertThat(response.myFeedback()).isNull();
        assertThat(response.helpfulCount()).isEqualTo(6);
        assertThat(response.notHelpfulCount()).isEqualTo(1);
    }

    @Test
    void missingPostReturnsNotFoundWithoutWritingFeedback() {
        when(posts.findByID("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.set(
                "authenticated_user", "missing", new FeedbackRequest(FeedbackType.HELPFUL)))
                .isInstanceOf(PostApiException.class)
                .satisfies(error -> {
                    PostApiException apiError = (PostApiException) error;
                    assertThat(apiError.status().value()).isEqualTo(404);
                    assertThat(apiError.code()).isEqualTo("POST_NOT_FOUND");
                });
        verify(feedback, never()).set(any());
    }

    @Test
    void postAuthorCannotSetOrRemoveFeedback() {
        assertThatThrownBy(() -> service.set(
                "author_1", "post_1", new FeedbackRequest(FeedbackType.HELPFUL)))
                .isInstanceOf(PostApiException.class)
                .satisfies(error -> assertThat(((PostApiException) error).status().value())
                        .isEqualTo(403));
        assertThatThrownBy(() -> service.remove("author_1", "post_1"))
                .isInstanceOf(PostApiException.class)
                .satisfies(error -> assertThat(((PostApiException) error).status().value())
                        .isEqualTo(403));
        verify(feedback, never()).set(any());
        verify(feedback, never()).remove(any(), any());
    }

    @Test
    void missingFeedbackTypeIsRejectedBeforeRepositoryAccess() {
        assertThatThrownBy(() -> service.set(
                "authenticated_user", "post_1", new FeedbackRequest(null)))
                .isInstanceOf(PostApiException.class)
                .satisfies(error -> assertThat(((PostApiException) error).status().value())
                        .isEqualTo(400));
        verify(feedback, never()).set(any());
    }

    private static CommunityPost post(String authorID) {
        return new CommunityPost(
                "post_1", authorID, "Post Author", "location_1", "Place", "Address",
                10.9, 106.7, "Cloudier here", NOW, List.of(),
                new ApiWeather("2026-09-14T08:29:00Z", 2, "Partly cloudy"),
                3, 6, 1, "COMMUNITY");
    }
}
