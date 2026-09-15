package com.the.weather.post.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.the.weather.post.model.ApiWeather;
import com.the.weather.post.model.CommunityPost;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

class DynamoDbPostRepositoryTests {

    @Test
    void locationFeedQueriesLocationIndexNewestFirstWithoutScan() {
        QueryRequest request = query(repository().findByLocation("location_1", 10, null));
        assertThat(request.indexName()).isEqualTo("LocationPostsIndex");
        assertThat(request.scanIndexForward()).isFalse();
        assertThat(request.keyConditionExpression()).contains("#partition");
    }

    @Test
    void globalFeedQueriesCommunityIndexNewestFirstWithoutScan() {
        QueryRequest request = query(repository().findCommunityFeed(10, null));
        assertThat(request.indexName()).isEqualTo("CommunityFeedIndex");
        assertThat(request.scanIndexForward()).isFalse();
        assertThat(request.expressionAttributeValues().get(":partition").s()).isEqualTo("COMMUNITY");
    }

    @Test
    void saveWritesUsernameWeatherAccuracyRatingAndFeedbackCounters() {
        DynamoDbPostRepository repository = repository();
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        repository.save(post());

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(captor.capture());
        Map<String, AttributeValue> item = captor.getValue().item();
        assertThat(item).containsEntry("username", AttributeValue.fromS("Example User"));
        assertThat(item).containsEntry("weatherAccuracyRating", AttributeValue.fromN("3"));
        assertThat(item).containsEntry("helpfulCount", AttributeValue.fromN("0"));
        assertThat(item).containsEntry("notHelpfulCount", AttributeValue.fromN("0"));
    }

    @Test
    void locationFeedReturnsWeatherAccuracyRatingAndFeedbackCounts() {
        DynamoDbPostRepository repository = repository();
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(List.of(item())).lastEvaluatedKey(Map.of()).build());

        CommunityPost post = repository.findByLocation("location_1", 10, null).items().getFirst();
        assertThat(post.weatherAccuracyRating()).isEqualTo(3);
        assertThat(post.helpfulCount()).isEqualTo(4);
        assertThat(post.notHelpfulCount()).isEqualTo(1);
    }

    @Test
    void legacyPostWithoutUsernameFallsBackToUserID() {
        DynamoDbPostRepository repository = repository();
        Map<String, AttributeValue> legacyItem = item();
        legacyItem.remove("username");
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(List.of(legacyItem)).lastEvaluatedKey(Map.of()).build());

        assertThat(repository.findByLocation("location_1", 10, null).items().getFirst()
                .username()).isEqualTo("user_1");
    }

    @Test
    void legacyPostWithoutFeedbackCountersDefaultsBothToZero() {
        DynamoDbPostRepository repository = repository();
        Map<String, AttributeValue> legacyItem = item();
        legacyItem.remove("helpfulCount");
        legacyItem.remove("notHelpfulCount");
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(List.of(legacyItem)).lastEvaluatedKey(Map.of()).build());

        CommunityPost post = repository.findByLocation("location_1", 10, null).items().getFirst();
        assertThat(post.helpfulCount()).isZero();
        assertThat(post.notHelpfulCount()).isZero();
    }

    private DynamoDbClient dynamoDb;

    private DynamoDbPostRepository repository() {
        dynamoDb = mock(DynamoDbClient.class);
        when(dynamoDb.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of()).lastEvaluatedKey(Map.of()).build());
        return new DynamoDbPostRepository(dynamoDb, "CommunityPosts");
    }

    private QueryRequest query(PostRepository.PostPage ignored) {
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDb).query(captor.capture());
        return captor.getValue();
    }

    private static CommunityPost post() {
        return new CommunityPost("post_1", "user_1", "Example User", "location_1", "Place", "Address",
                10.9, 106.7, "Windy", Instant.parse("2026-09-13T01:36:00Z"),
                List.of("image-key"), new ApiWeather("2026-09-13T01:35:55Z", 3, "Overcast"),
                3, 0, 0, "COMMUNITY");
    }

    private static Map<String, AttributeValue> item() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("postID", AttributeValue.fromS("post_1"));
        item.put("userID", AttributeValue.fromS("user_1"));
        item.put("username", AttributeValue.fromS("Example User"));
        item.put("locationID", AttributeValue.fromS("location_1"));
        item.put("locationName", AttributeValue.fromS("Place"));
        item.put("address", AttributeValue.fromS("Address"));
        item.put("latitude", AttributeValue.fromN("10.9"));
        item.put("longitude", AttributeValue.fromN("106.7"));
        item.put("description", AttributeValue.fromS("Windy"));
        item.put("createdAt", AttributeValue.fromS("2026-09-13T01:36:00Z"));
        item.put("imageKeys", AttributeValue.fromL(List.of(AttributeValue.fromS("image-key"))));
        item.put("apiWeather", AttributeValue.fromM(Map.of(
                        "recordedAt", AttributeValue.fromS("2026-09-13T01:35:55Z"),
                        "weather_code", AttributeValue.fromN("3"),
                        "condition", AttributeValue.fromS("Overcast"))));
        item.put("weatherAccuracyRating", AttributeValue.fromN("3"));
        item.put("helpfulCount", AttributeValue.fromN("4"));
        item.put("notHelpfulCount", AttributeValue.fromN("1"));
        item.put("feedType", AttributeValue.fromS("COMMUNITY"));
        return item;
    }
}
