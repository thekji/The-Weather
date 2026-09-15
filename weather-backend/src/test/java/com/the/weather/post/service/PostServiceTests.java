package com.the.weather.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.the.weather.feedback.model.FeedbackType;
import com.the.weather.feedback.repository.FeedbackRepository;
import com.the.weather.post.dto.CreatePostRequest;
import com.the.weather.post.exception.PostApiException;
import com.the.weather.post.model.ApiWeather;
import com.the.weather.post.model.CommunityPost;
import com.the.weather.post.repository.PostRepository;
import com.the.weather.storage.service.S3MediaService;
import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.service.CurrentWeatherService;

class PostServiceTests {
    private final PostRepository posts = mock(PostRepository.class);
    private final FeedbackRepository feedback = mock(FeedbackRepository.class);
    private final S3MediaService media = mock(S3MediaService.class);
    private final CurrentWeatherService weather = mock(CurrentWeatherService.class);
    private final Instant now = Instant.parse("2026-09-13T01:36:00Z");
    private PostService service;

    @BeforeEach
    void setUp() {
        service = new PostService(posts, feedback, media, weather,
                Clock.fixed(now, ZoneOffset.UTC));
        when(weather.current(10.9, 106.7)).thenReturn(new CurrentWeatherDto(
                10.9, 106.7, "2026-09-13T01:35:55Z", "Asia/Ho_Chi_Minh",
                30, 34, 70, 0, 0, 90, 10, 180, 3, "Overcast", true));
        when(media.createReadUrls(any())).thenReturn(List.of());
    }

    @Test
    void createsTextOnlyPostWithServerOwnedFieldsAndSimplifiedWeather() {
        var response = service.create("user_1", "Example User", "geoapify:place", request(), List.of());
        ArgumentCaptor<CommunityPost> captor = ArgumentCaptor.forClass(CommunityPost.class);
        verify(posts).save(captor.capture());
        CommunityPost saved = captor.getValue();
        assertThat(saved.postID()).startsWith("post_");
        assertThat(saved.userID()).isEqualTo("user_1");
        assertThat(saved.username()).isEqualTo("Example User");
        assertThat(saved.createdAt()).isEqualTo(now);
        assertThat(saved.imageKeys()).isEmpty();
        assertThat(saved.apiWeather()).isEqualTo(new ApiWeather(
                "2026-09-13T01:35:55Z", 3, "Overcast"));
        assertThat(saved.weatherAccuracyRating()).isEqualTo(3);
        assertThat(saved.helpfulCount()).isZero();
        assertThat(saved.notHelpfulCount()).isZero();
        assertThat(saved.feedType()).isEqualTo("COMMUNITY");
        assertThat(response.postID()).isEqualTo(saved.postID());
        assertThat(response.weatherAccuracyRating()).isEqualTo(3);
        assertThat(response.helpfulCount()).isZero();
        assertThat(response.notHelpfulCount()).isZero();
    }

    @Test
    void acceptsBoundaryWeatherAccuracyRatings() {
        service.create("user_1", "Example User", "geoapify:place", request(1), List.of());
        service.create("user_1", "Example User", "geoapify:place", request(5), List.of());

        verify(posts, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void rejectsMissingAndOutOfRangeWeatherAccuracyRatings() {
        assertThatThrownBy(() -> service.create("user_1", "Example User", "geoapify:place", request(null), List.of()))
                .isInstanceOf(PostApiException.class).hasMessageContaining("invalid");
        assertThatThrownBy(() -> service.create("user_1", "Example User", "geoapify:place", request(0), List.of()))
                .isInstanceOf(PostApiException.class).hasMessageContaining("invalid");
        assertThatThrownBy(() -> service.create("user_1", "Example User", "geoapify:place", request(6), List.of()))
                .isInstanceOf(PostApiException.class).hasMessageContaining("invalid");
    }

    @Test
    void uploadsAndPersistsThreeServerGeneratedImageKeys() {
        List<MultipartFile> images = List.of(image("a.jpg"), image("b.jpg"), image("c.jpg"));
        when(media.upload(eq("user_1"), any(), any())).thenReturn("key-1", "key-2", "key-3");
        service.create("user_1", "Example User", "geoapify:place", request(), images);
        ArgumentCaptor<CommunityPost> captor = ArgumentCaptor.forClass(CommunityPost.class);
        verify(posts).save(captor.capture());
        assertThat(captor.getValue().imageKeys()).containsExactly("key-1", "key-2", "key-3");
    }

    @Test
    void rejectsInvalidPostBeforeCallingWeather() {
        assertThatThrownBy(() -> service.create("user_1", "Example User", "", request(), List.of()))
                .isInstanceOf(PostApiException.class).hasMessageContaining("invalid");
    }

    @Test
    void ownerDeleteRemovesPostImages() {
        CommunityPost post = post("user_1", List.of("one", "two"));
        when(posts.findByID("post_1")).thenReturn(Optional.of(post));
        service.delete("user_1", "post_1");
        InOrder deletion = inOrder(posts, feedback, media);
        deletion.verify(posts).delete("post_1");
        deletion.verify(feedback).deleteAllByPostID("post_1");
        deletion.verify(media).deleteAll(List.of("one", "two"));
    }

    @Test
    void locationAndGlobalFeedsReturnTheStoredRatingAndFeedbackCounts() {
        when(posts.findByLocation("geoapify:place", 10, null)).thenReturn(
                new PostRepository.PostPage(List.of(post("user_1", List.of())), null));
        when(posts.findCommunityFeed(10, null)).thenReturn(
                new PostRepository.PostPage(List.of(post("user_1", List.of())), null));

        when(feedback.findByPostIDsAndUserID(any(), eq("viewer_1")))
                .thenReturn(Map.of("post_1", FeedbackType.HELPFUL));

        var locationPost = service.byLocation(
                "viewer_1", "geoapify:place", null, null).items().getFirst();
        var communityPost = service.communityFeed("viewer_1", null, null).items().getFirst();
        assertThat(locationPost.weatherAccuracyRating()).isEqualTo(3);
        assertThat(locationPost.helpfulCount()).isEqualTo(4);
        assertThat(locationPost.notHelpfulCount()).isEqualTo(1);
        assertThat(locationPost.myFeedback()).isEqualTo(FeedbackType.HELPFUL);
        assertThat(communityPost.weatherAccuracyRating()).isEqualTo(3);
        assertThat(communityPost.helpfulCount()).isEqualTo(4);
        assertThat(communityPost.notHelpfulCount()).isEqualTo(1);
        assertThat(communityPost.myFeedback()).isEqualTo(FeedbackType.HELPFUL);
    }

    @Test
    void anotherUserCannotDeletePost() {
        when(posts.findByID("post_1")).thenReturn(Optional.of(post("owner", List.of())));
        assertThatThrownBy(() -> service.delete("other", "post_1"))
                .isInstanceOf(PostApiException.class).extracting("status.value").isEqualTo(403);
    }

    private static CreatePostRequest request() {
        return request(3);
    }

    private static CreatePostRequest request(Integer weatherAccuracyRating) {
        return new CreatePostRequest(" Windy here ", "Petrolimex", "Thuan An", 10.9, 106.7,
                weatherAccuracyRating);
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("images", name, "image/jpeg", new byte[] {1, 2});
    }

    private CommunityPost post(String owner, List<String> keys) {
        return new CommunityPost("post_1", owner, "Example User", "geoapify:place", "Place", "Address",
                10.9, 106.7, "Windy", now, keys,
                new ApiWeather("time", 3, "Overcast"), 3, 4, 1, "COMMUNITY");
    }
}
