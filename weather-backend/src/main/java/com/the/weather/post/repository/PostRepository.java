package com.the.weather.post.repository;

import java.util.Optional;

import com.the.weather.post.model.CommunityPost;

public interface PostRepository {
    void save(CommunityPost post);
    Optional<CommunityPost> findByID(String postID);
    PostPage findByLocation(String locationID, int limit, String cursor);
    PostPage findCommunityFeed(int limit, String cursor);
    void delete(String postID);

    record PostPage(java.util.List<CommunityPost> items, String nextCursor) {}
}
