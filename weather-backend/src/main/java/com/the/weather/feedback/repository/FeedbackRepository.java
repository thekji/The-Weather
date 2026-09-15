package com.the.weather.feedback.repository;

import java.util.List;
import java.util.Map;

import com.the.weather.feedback.model.FeedbackType;
import com.the.weather.feedback.model.PostFeedback;

public interface FeedbackRepository {

    FeedbackStateResult set(PostFeedback feedback);

    FeedbackStateResult remove(String postID, String userID);

    Map<String, FeedbackType> findByPostIDsAndUserID(List<String> postIDs, String userID);

    void deleteAllByPostID(String postID);

    record FeedbackStateResult(
            FeedbackType myFeedback,
            int helpfulCount,
            int notHelpfulCount) {}
}
