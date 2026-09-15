package com.the.weather.star.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.the.weather.star.model.StarredLocation;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

@Repository
public class DynamoDbStarRepository implements StarRepository {

    private static final String USER_ID = "userID";
    private static final String LOCATION_ID = "locationID";

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoDbStarRepository(
            DynamoDbClient dynamoDb,
            @Value("${dynamodb.starred-locations-table}") String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public List<StarredLocation> findAllByUserID(String userID) {
        List<StarredLocation> stars = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = Map.of();

        do {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("#userID = :userID")
                    .expressionAttributeNames(Map.of("#userID", USER_ID))
                    .expressionAttributeValues(Map.of(
                            ":userID",
                            AttributeValue.fromS(userID)))
                    .consistentRead(true);
            if (!lastEvaluatedKey.isEmpty()) {
                request.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse response = dynamoDb.query(request.build());
            response.items().stream().map(DynamoDbStarRepository::toStar).forEach(stars::add);
            lastEvaluatedKey = response.lastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return List.copyOf(stars);
    }

    @Override
    public Optional<StarredLocation> findByUserIDAndLocationID(
            String userID,
            String locationID) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key(userID, locationID))
                .consistentRead(true)
                .build();

        Map<String, AttributeValue> item = dynamoDb.getItem(request).item();
        return item.isEmpty() ? Optional.empty() : Optional.of(toStar(item));
    }

    @Override
    public boolean saveIfAbsent(StarredLocation location) {
        Map<String, AttributeValue> item = Map.of(
                USER_ID, AttributeValue.fromS(location.userID()),
                LOCATION_ID, AttributeValue.fromS(location.locationID()),
                "name", AttributeValue.fromS(location.name()),
                "address", AttributeValue.fromS(location.address()),
                "latitude", AttributeValue.fromN(Double.toString(location.latitude())),
                "longitude", AttributeValue.fromN(Double.toString(location.longitude())),
                "starredAt", AttributeValue.fromS(location.starredAt().toString()));

        try {
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression(
                            "attribute_not_exists(#userID) AND attribute_not_exists(#locationID)")
                    .expressionAttributeNames(Map.of(
                            "#userID", USER_ID,
                            "#locationID", LOCATION_ID))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }

    @Override
    public void deleteByUserIDAndLocationID(String userID, String locationID) {
        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key(userID, locationID))
                .build());
    }

    private static Map<String, AttributeValue> key(String userID, String locationID) {
        return Map.of(
                USER_ID, AttributeValue.fromS(userID),
                LOCATION_ID, AttributeValue.fromS(locationID));
    }

    private static StarredLocation toStar(Map<String, AttributeValue> item) {
        return new StarredLocation(
                item.get(USER_ID).s(),
                item.get(LOCATION_ID).s(),
                item.get("name").s(),
                item.get("address").s(),
                Double.parseDouble(item.get("latitude").n()),
                Double.parseDouble(item.get("longitude").n()),
                Instant.parse(item.get("starredAt").s()));
    }
}
