package com.the.weather.post.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.the.weather.feedback.model.FeedbackType;
import com.the.weather.feedback.repository.FeedbackRepository;
import com.the.weather.post.dto.CreatePostRequest;
import com.the.weather.post.dto.PostPageResponse;
import com.the.weather.post.dto.PostResponse;
import com.the.weather.post.exception.PostApiException;
import com.the.weather.post.model.ApiWeather;
import com.the.weather.post.model.CommunityPost;
import com.the.weather.post.repository.PostRepository;
import com.the.weather.storage.service.S3MediaService;
import com.the.weather.weather.dto.CurrentWeatherDto;
import com.the.weather.weather.service.CurrentWeatherService;

@Service
public class PostService {

    private static final int DESCRIPTION_LIMIT = 1000;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository posts;
    private final FeedbackRepository feedback;
    private final S3MediaService media;
    private final CurrentWeatherService weather;
    private final Clock clock;

    public PostService(PostRepository posts, FeedbackRepository feedback, S3MediaService media,
            CurrentWeatherService weather, Clock clock) {
        this.posts = posts;
        this.feedback = feedback;
        this.media = media;
        this.weather = weather;
        this.clock = clock;
    }

    public PostResponse create(String userID, String username, String locationID, CreatePostRequest request,
            List<MultipartFile> suppliedImages) {
        validate(userID, username, locationID, request);
        List<MultipartFile> images = suppliedImages == null ? List.of() : suppliedImages;
        media.validateImages(images);
        String postID = "post_" + UUID.randomUUID();
        Instant createdAt = Instant.now(clock);
        CurrentWeatherDto current = weather.current(request.latitude(), request.longitude());
        ApiWeather snapshot = new ApiWeather(current.recordedAt(), current.weatherCode(), current.condition());
        List<String> keys = new ArrayList<>();
        try {
            for (MultipartFile image : images) keys.add(media.upload(userID, postID, image));
            CommunityPost post = new CommunityPost(postID, userID, username, locationID,
                    request.locationName().trim(), request.address().trim(), request.latitude(),
                    request.longitude(), request.description().trim(), createdAt, List.copyOf(keys),
                    snapshot, request.weatherAccuracyRating(), 0, 0, "COMMUNITY");
            posts.save(post);
            return response(post, null);
        } catch (RuntimeException exception) {
            media.deleteAll(keys);
            throw exception;
        }
    }

    public PostPageResponse byLocation(
            String authenticatedUserID,
            String locationID,
            Integer requestedLimit,
            String cursor) {
        if (!StringUtils.hasText(locationID)) invalid("Location is required.");
        return page(posts.findByLocation(locationID, pageSize(requestedLimit), cursor),
                authenticatedUserID);
    }

    public PostPageResponse communityFeed(
            String authenticatedUserID,
            Integer requestedLimit,
            String cursor) {
        return page(posts.findCommunityFeed(pageSize(requestedLimit), cursor), authenticatedUserID);
    }

    public PostResponse find(String authenticatedUserID, String postID) {
        CommunityPost post = requirePost(postID);
        FeedbackType myFeedback = feedback.findByPostIDsAndUserID(
                List.of(postID), authenticatedUserID).get(postID);
        return response(post, myFeedback);
    }

    public void delete(String userID, String postID) {
        CommunityPost post = requirePost(postID);
        if (!post.userID().equals(userID)) {
            throw new PostApiException(HttpStatus.FORBIDDEN, "POST_FORBIDDEN",
                    "You can only delete your own community posts.");
        }
        posts.delete(postID);
        feedback.deleteAllByPostID(postID);
        media.deleteAll(post.imageKeys());
    }

    private PostPageResponse page(PostRepository.PostPage page, String authenticatedUserID) {
        Map<String, FeedbackType> viewerFeedback = feedback.findByPostIDsAndUserID(
                page.items().stream().map(CommunityPost::postID).toList(),
                authenticatedUserID);
        return new PostPageResponse(page.items().stream()
                .map(post -> response(post, viewerFeedback.get(post.postID())))
                .toList(), page.nextCursor());
    }

    private PostResponse response(CommunityPost post, FeedbackType myFeedback) {
        return PostResponse.from(post, media.createReadUrls(post.imageKeys()), myFeedback);
    }

    private CommunityPost requirePost(String postID) {
        if (!StringUtils.hasText(postID)) throw notFound();
        return posts.findByID(postID).orElseThrow(PostService::notFound);
    }

    private static void validate(String userID, String username, String locationID,
            CreatePostRequest request) {
        if (!StringUtils.hasText(userID) || !StringUtils.hasText(username)
                || !StringUtils.hasText(locationID) || request == null
                || !StringUtils.hasText(request.locationName()) || !StringUtils.hasText(request.address())
                || !StringUtils.hasText(request.description())
                || request.description().trim().length() > DESCRIPTION_LIMIT
                || request.weatherAccuracyRating() == null
                || request.weatherAccuracyRating() < 1
                || request.weatherAccuracyRating() > 5
                || !coordinate(request.latitude(), -90, 90)
                || !coordinate(request.longitude(), -180, 180)) {
            invalid("Post, description, or location details are invalid.");
        }
    }

    private static int pageSize(Integer limit) {
        if (limit == null) return DEFAULT_PAGE_SIZE;
        if (limit < 1 || limit > MAX_PAGE_SIZE) invalid("limit must be between 1 and 50.");
        return limit;
    }

    private static boolean coordinate(Double value, double min, double max) {
        return value != null && Double.isFinite(value) && value >= min && value <= max;
    }

    private static void invalid(String message) {
        throw new PostApiException(HttpStatus.BAD_REQUEST, "INVALID_POST", message);
    }

    private static PostApiException notFound() {
        return new PostApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND",
                "The community post was not found.");
    }
}
