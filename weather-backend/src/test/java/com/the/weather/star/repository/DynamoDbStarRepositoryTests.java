package com.the.weather.star.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.the.weather.star.model.StarredLocation;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

class DynamoDbStarRepositoryTests {

    private static final Instant STARRED_AT = Instant.parse("2026-09-08T01:00:00Z");

    private final RecordingDynamoDbClient recordingClient = new RecordingDynamoDbClient();
    private final DynamoDbStarRepository repository =
            new DynamoDbStarRepository(recordingClient.client(), "StarredLocations");

    @Test
    void listUsesAPartitionKeyQueryAndPaginatesWithoutScanning() {
        recordingClient.queryResponses.add(QueryResponse.builder()
                .items(List.of(item("user_123", "location_1")))
                .lastEvaluatedKey(key("user_123", "location_1"))
                .build());
        recordingClient.queryResponses.add(QueryResponse.builder()
                .items(List.of(item("user_123", "location_2")))
                .build());

        List<StarredLocation> result = repository.findAllByUserID("user_123");

        assertThat(result).extracting(StarredLocation::locationID)
                .containsExactly("location_1", "location_2");
        assertThat(recordingClient.queryRequests).hasSize(2);
        QueryRequest first = recordingClient.queryRequests.get(0);
        assertThat(first.tableName()).isEqualTo("StarredLocations");
        assertThat(first.keyConditionExpression()).isEqualTo("#userID = :userID");
        assertThat(first.expressionAttributeNames()).containsEntry("#userID", "userID");
        assertThat(first.expressionAttributeValues())
                .containsEntry(":userID", AttributeValue.fromS("user_123"));
        assertThat(first.consistentRead()).isTrue();
        assertThat(first.exclusiveStartKey()).isEmpty();
        assertThat(recordingClient.queryRequests.get(1).exclusiveStartKey())
                .isEqualTo(key("user_123", "location_1"));
        assertThat(recordingClient.scanCalled).isFalse();
    }

    @Test
    void listReturnsAnEmptyListWhenTheUserHasNoStars() {
        recordingClient.queryResponses.add(QueryResponse.builder().build());

        assertThat(repository.findAllByUserID("user_without_stars")).isEmpty();
        assertThat(recordingClient.queryRequests).hasSize(1);
        assertThat(recordingClient.scanCalled).isFalse();
    }

    @Test
    void saveWritesTheFrozenSchemaWithConditionalDuplicateProtection() {
        StarredLocation location = star("user_123", "location_1");

        assertThat(repository.saveIfAbsent(location)).isTrue();

        PutItemRequest request = recordingClient.putItemRequest;
        assertThat(request.tableName()).isEqualTo("StarredLocations");
        assertThat(request.item()).containsEntry("userID", AttributeValue.fromS("user_123"));
        assertThat(request.item()).containsEntry("locationID", AttributeValue.fromS("location_1"));
        assertThat(request.item()).containsEntry("name", AttributeValue.fromS("RMIT University"));
        assertThat(request.item()).containsEntry(
                "address",
                AttributeValue.fromS("702 Nguyen Van Linh, Ho Chi Minh City"));
        assertThat(request.item()).containsEntry("latitude", AttributeValue.fromN("10.729"));
        assertThat(request.item()).containsEntry("longitude", AttributeValue.fromN("106.694"));
        assertThat(request.item()).containsEntry(
                "starredAt",
                AttributeValue.fromS("2026-09-08T01:00:00Z"));
        assertThat(request.item()).containsEntry(
                "weatherAlertsEnabled",
                AttributeValue.fromBool(false));
        assertThat(request.item()).hasSize(8);
        assertThat(request.conditionExpression()).isEqualTo(
                "attribute_not_exists(#userID) AND attribute_not_exists(#locationID)");
        assertThat(request.expressionAttributeNames())
                .containsEntry("#userID", "userID")
                .containsEntry("#locationID", "locationID");
    }

    @Test
    void saveReportsADuplicateWithoutOverwritingTheExistingItem() {
        recordingClient.failPutAsDuplicate = true;

        assertThat(repository.saveIfAbsent(star("user_123", "location_1"))).isFalse();
    }

    @Test
    void findGetsOneExactCompositeKeyWithStrongConsistency() {
        recordingClient.getItemResponse = GetItemResponse.builder()
                .item(item("user_123", "location_1"))
                .build();

        StarredLocation result = repository
                .findByUserIDAndLocationID("user_123", "location_1")
                .orElseThrow();

        assertThat(result.userID()).isEqualTo("user_123");
        assertThat(result.locationID()).isEqualTo("location_1");
        assertThat(recordingClient.getItemRequest.tableName()).isEqualTo("StarredLocations");
        assertThat(recordingClient.getItemRequest.key())
                .isEqualTo(key("user_123", "location_1"));
        assertThat(recordingClient.getItemRequest.consistentRead()).isTrue();
        assertThat(recordingClient.scanCalled).isFalse();
    }

    @Test
    void deleteUsesTheAuthenticatedUserAndLocationCompositeKeyWithoutAReadOrScan() {
        repository.deleteByUserIDAndLocationID("user_123", "location_1");

        assertThat(recordingClient.deleteItemRequest.tableName()).isEqualTo("StarredLocations");
        assertThat(recordingClient.deleteItemRequest.key())
                .isEqualTo(key("user_123", "location_1"));
        assertThat(recordingClient.queryRequests).isEmpty();
        assertThat(recordingClient.getItemRequest).isNull();
        assertThat(recordingClient.scanCalled).isFalse();
    }

    private static StarredLocation star(String userID, String locationID) {
        return new StarredLocation(
                userID,
                locationID,
                "RMIT University",
                "702 Nguyen Van Linh, Ho Chi Minh City",
                10.729,
                106.694,
                STARRED_AT,
                false);
    }

    private static Map<String, AttributeValue> item(String userID, String locationID) {
        StarredLocation star = star(userID, locationID);
        return Map.of(
                "userID", AttributeValue.fromS(star.userID()),
                "locationID", AttributeValue.fromS(star.locationID()),
                "name", AttributeValue.fromS(star.name()),
                "address", AttributeValue.fromS(star.address()),
                "latitude", AttributeValue.fromN(Double.toString(star.latitude())),
                "longitude", AttributeValue.fromN(Double.toString(star.longitude())),
                "starredAt", AttributeValue.fromS(star.starredAt().toString()),
                "weatherAlertsEnabled", AttributeValue.fromBool(star.weatherAlertsEnabled()));
    }

    private static Map<String, AttributeValue> key(String userID, String locationID) {
        return Map.of(
                "userID", AttributeValue.fromS(userID),
                "locationID", AttributeValue.fromS(locationID));
    }

    private static final class RecordingDynamoDbClient {
        private final Deque<QueryResponse> queryResponses = new ArrayDeque<>();
        private final List<QueryRequest> queryRequests = new ArrayList<>();
        private GetItemResponse getItemResponse = GetItemResponse.builder().build();
        private GetItemRequest getItemRequest;
        private PutItemRequest putItemRequest;
        private DeleteItemRequest deleteItemRequest;
        private boolean failPutAsDuplicate;
        private boolean scanCalled;

        private DynamoDbClient client() {
            return (DynamoDbClient) Proxy.newProxyInstance(
                    DynamoDbClient.class.getClassLoader(),
                    new Class<?>[] {DynamoDbClient.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("query")
                                && arguments != null
                                && arguments.length == 1
                                && arguments[0] instanceof QueryRequest request) {
                            queryRequests.add(request);
                            return queryResponses.removeFirst();
                        }
                        if (method.getName().equals("getItem")
                                && arguments != null
                                && arguments.length == 1
                                && arguments[0] instanceof GetItemRequest request) {
                            getItemRequest = request;
                            return getItemResponse;
                        }
                        if (method.getName().equals("putItem")
                                && arguments != null
                                && arguments.length == 1
                                && arguments[0] instanceof PutItemRequest request) {
                            putItemRequest = request;
                            if (failPutAsDuplicate) {
                                throw ConditionalCheckFailedException.builder()
                                        .message("Star already exists")
                                        .build();
                            }
                            return PutItemResponse.builder().build();
                        }
                        if (method.getName().equals("deleteItem")
                                && arguments != null
                                && arguments.length == 1
                                && arguments[0] instanceof DeleteItemRequest request) {
                            deleteItemRequest = request;
                            return DeleteItemResponse.builder().build();
                        }
                        if (method.getName().equals("scan")) {
                            scanCalled = true;
                            throw new AssertionError("Star repository must not scan DynamoDB");
                        }
                        if (method.getName().equals("serviceName")) {
                            return "dynamodb";
                        }
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
