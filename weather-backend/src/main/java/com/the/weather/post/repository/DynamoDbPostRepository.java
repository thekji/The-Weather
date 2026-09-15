package com.the.weather.post.repository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.the.weather.post.exception.PostApiException;
import com.the.weather.post.model.ApiWeather;
import com.the.weather.post.model.CommunityPost;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

@Repository
public class DynamoDbPostRepository implements PostRepository {

    static final String LOCATION_INDEX = "LocationPostsIndex";
    static final String COMMUNITY_INDEX = "CommunityFeedIndex";
    private static final TypeReference<Map<String, String>> CURSOR_TYPE = new TypeReference<>() {};

    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String tableName;

    public DynamoDbPostRepository(DynamoDbClient dynamoDb,
            @Value("${dynamodb.community-posts-table}") String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public void save(CommunityPost post) {
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(toItem(post))
                .conditionExpression("attribute_not_exists(postID)").build());
    }

    @Override
    public Optional<CommunityPost> findByID(String postID) {
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName).key(Map.of("postID", AttributeValue.fromS(postID)))
                .consistentRead(true).build()).item();
        return item.isEmpty() ? Optional.empty() : Optional.of(fromItem(item));
    }

    @Override
    public PostPage findByLocation(String locationID, int limit, String cursor) {
        return query(LOCATION_INDEX, "locationID", locationID, limit, cursor);
    }

    @Override
    public PostPage findCommunityFeed(int limit, String cursor) {
        return query(COMMUNITY_INDEX, "feedType", "COMMUNITY", limit, cursor);
    }

    @Override
    public void delete(String postID) {
        dynamoDb.deleteItem(DeleteItemRequest.builder().tableName(tableName)
                .key(Map.of("postID", AttributeValue.fromS(postID))).build());
    }

    private PostPage query(String index, String partitionName, String partitionValue,
            int limit, String cursor) {
        QueryRequest.Builder request = QueryRequest.builder().tableName(tableName).indexName(index)
                .keyConditionExpression("#partition = :partition")
                .expressionAttributeNames(Map.of("#partition", partitionName))
                .expressionAttributeValues(Map.of(":partition", AttributeValue.fromS(partitionValue)))
                .scanIndexForward(false).limit(limit);
        Map<String, AttributeValue> startKey = decodeCursor(cursor);
        if (!startKey.isEmpty()) request.exclusiveStartKey(startKey);
        QueryResponse response = dynamoDb.query(request.build());
        return new PostPage(response.items().stream().map(DynamoDbPostRepository::fromItem).toList(),
                encodeCursor(response.lastEvaluatedKey()));
    }

    private String encodeCursor(Map<String, AttributeValue> key) {
        if (key == null || key.isEmpty()) return null;
        try {
            Map<String, String> values = new HashMap<>();
            key.forEach((name, value) -> values.put(name, value.s()));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(values));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encode DynamoDB cursor", exception);
        }
    }

    private Map<String, AttributeValue> decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return Map.of();
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor.getBytes(StandardCharsets.UTF_8));
            Map<String, String> values = objectMapper.readValue(json, CURSOR_TYPE);
            Map<String, AttributeValue> key = new HashMap<>();
            values.forEach((name, value) -> key.put(name, AttributeValue.fromS(value)));
            return key;
        } catch (Exception exception) {
            throw new PostApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR",
                    "The pagination cursor is invalid.");
        }
    }

    private static Map<String, AttributeValue> toItem(CommunityPost post) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("postID", AttributeValue.fromS(post.postID()));
        item.put("userID", AttributeValue.fromS(post.userID()));
        item.put("username", AttributeValue.fromS(post.username()));
        item.put("locationID", AttributeValue.fromS(post.locationID()));
        item.put("locationName", AttributeValue.fromS(post.locationName()));
        item.put("address", AttributeValue.fromS(post.address()));
        item.put("latitude", AttributeValue.fromN(Double.toString(post.latitude())));
        item.put("longitude", AttributeValue.fromN(Double.toString(post.longitude())));
        item.put("description", AttributeValue.fromS(post.description()));
        item.put("createdAt", AttributeValue.fromS(post.createdAt().toString()));
        item.put("imageKeys", AttributeValue.fromL(post.imageKeys().stream().map(AttributeValue::fromS).toList()));
        item.put("apiWeather", AttributeValue.fromM(Map.of(
                "recordedAt", AttributeValue.fromS(post.apiWeather().recordedAt()),
                "weather_code", AttributeValue.fromN(Integer.toString(post.apiWeather().weatherCode())),
                "condition", AttributeValue.fromS(post.apiWeather().condition()))));
        item.put("weatherAccuracyRating", AttributeValue.fromN(Integer.toString(post.weatherAccuracyRating())));
        item.put("helpfulCount", AttributeValue.fromN(Integer.toString(post.helpfulCount())));
        item.put("notHelpfulCount", AttributeValue.fromN(Integer.toString(post.notHelpfulCount())));
        item.put("feedType", AttributeValue.fromS(post.feedType()));
        return item;
    }

    private static CommunityPost fromItem(Map<String, AttributeValue> item) {
        Map<String, AttributeValue> weather = item.get("apiWeather").m();
        List<String> imageKeys = item.containsKey("imageKeys")
                ? item.get("imageKeys").l().stream().map(AttributeValue::s).toList() : List.of();
        String userID = item.get("userID").s();
        String username = item.containsKey("username") ? item.get("username").s() : userID;
        return new CommunityPost(item.get("postID").s(), userID, username,
                item.get("locationID").s(), item.get("locationName").s(), item.get("address").s(),
                Double.parseDouble(item.get("latitude").n()), Double.parseDouble(item.get("longitude").n()),
                item.get("description").s(), Instant.parse(item.get("createdAt").s()), imageKeys,
                new ApiWeather(weather.get("recordedAt").s(),
                        Integer.parseInt(weather.get("weather_code").n()), weather.get("condition").s()),
                Integer.parseInt(item.get("weatherAccuracyRating").n()),
                count(item, "helpfulCount"), count(item, "notHelpfulCount"), item.get("feedType").s());
    }

    private static int count(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null || value.n() == null ? 0 : Integer.parseInt(value.n());
    }
}
