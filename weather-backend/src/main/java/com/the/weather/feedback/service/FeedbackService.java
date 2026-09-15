package com.the.weather.feedback.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.the.weather.feedback.dto.FeedbackRequest;
import com.the.weather.feedback.dto.FeedbackResponse;
import com.the.weather.feedback.model.PostFeedback;
import com.the.weather.feedback.repository.FeedbackRepository;
import com.the.weather.feedback.repository.FeedbackRepository.FeedbackStateResult;
import com.the.weather.post.exception.PostApiException;
import com.the.weather.post.model.CommunityPost;
import com.the.weather.post.repository.PostRepository;

@Service
public class FeedbackService {

    private final PostRepository posts;
    private final FeedbackRepository feedback;
    private final Clock clock;

    public FeedbackService(PostRepository posts, FeedbackRepository feedback, Clock clock) {
        this.posts = posts;
        this.feedback = feedback;
        this.clock = clock;
    }

    public FeedbackResponse set(
            String authenticatedUserID,
            String postID,
            FeedbackRequest request) {
        CommunityPost post = validateUserAndPost(authenticatedUserID, postID);
        if (request == null || request.feedbackType() == null) invalidFeedback();
        preventSelfFeedback(post, authenticatedUserID);

        FeedbackStateResult result = feedback.set(new PostFeedback(
                postID,
                authenticatedUserID,
                request.feedbackType(),
                Instant.now(clock)));

        return response(postID, result);
    }

    public FeedbackResponse remove(String authenticatedUserID, String postID) {
        CommunityPost post = validateUserAndPost(authenticatedUserID, postID);
        preventSelfFeedback(post, authenticatedUserID);
        return response(postID, feedback.remove(postID, authenticatedUserID));
    }

    private CommunityPost validateUserAndPost(String authenticatedUserID, String postID) {
        if (!StringUtils.hasText(authenticatedUserID) || !StringUtils.hasText(postID)) {
            invalidFeedback();
        }
        return posts.findByID(postID).orElseThrow(() ->
                new PostApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND",
                        "The community post was not found."));
    }

    private static void preventSelfFeedback(CommunityPost post, String authenticatedUserID) {
        if (post.userID().equals(authenticatedUserID)) {
            throw new PostApiException(HttpStatus.FORBIDDEN, "SELF_FEEDBACK_NOT_ALLOWED",
                    "You cannot provide feedback on your own post.");
        }
    }

    private static void invalidFeedback() {
        throw new PostApiException(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK",
                "Feedback type must be HELPFUL or NOT_HELPFUL.");
    }

    private static FeedbackResponse response(String postID, FeedbackStateResult result) {
        return new FeedbackResponse(
                postID,
                result.myFeedback(),
                result.helpfulCount(),
                result.notHelpfulCount());
    }
}
