package com.the.weather.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.the.weather.user.model.User;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

class DynamoDbUserRepositoryTests {

    private final RecordingDynamoDbClient recordingClient = new RecordingDynamoDbClient();
    private final DynamoDbClient dynamoDb = recordingClient.client();
    private final DynamoDbUserRepository repository =
            new DynamoDbUserRepository(dynamoDb, "Users");

    @Test
    void findByEmailGetsTheUserByPrimaryKey() {
        recordingClient.getItemResponse = GetItemResponse.builder()
                .item(Map.of(
                        "userID", AttributeValue.fromS("user_123"),
                        "email", AttributeValue.fromS("user@example.com"),
                        "name", AttributeValue.fromS("Example User"),
                        "passwordHash", AttributeValue.fromS("bcrypt-hash"),
                        "createdAt", AttributeValue.fromS("2026-09-07T00:00:00Z")))
                .build();

        User user = repository.findByEmail("user@example.com").orElseThrow();

        GetItemRequest request = recordingClient.getItemRequest;
        assertThat(request.tableName()).isEqualTo("Users");
        assertThat(request.key().get("email").s())
                .isEqualTo("user@example.com");
        assertThat(request.consistentRead()).isTrue();
        assertThat(recordingClient.queryCalled).isFalse();

        assertThat(user.userID()).isEqualTo("user_123");
        assertThat(user.passwordHash()).isEqualTo("bcrypt-hash");
    }

    @Test
    void findByEmailReturnsEmptyWhenTheEmailDoesNotExist() {
        recordingClient.getItemResponse = GetItemResponse.builder().build();

        assertThat(repository.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void saveUsesAConditionalWriteToGuaranteeUniqueEmail() {
        User user = new User(
                "user_123",
                "user@example.com",
                "Example User",
                "bcrypt-hash",
                Instant.parse("2026-09-07T00:00:00Z"));

        assertThat(repository.saveIfEmailAvailable(user)).isTrue();

        PutItemRequest request = recordingClient.putItemRequest;
        assertThat(request.tableName()).isEqualTo("Users");
        assertThat(request.item()).containsEntry("userID", AttributeValue.fromS("user_123"));
        assertThat(request.item()).containsEntry("email", AttributeValue.fromS("user@example.com"));
        assertThat(request.item()).containsEntry("name", AttributeValue.fromS("Example User"));
        assertThat(request.item()).containsEntry("passwordHash", AttributeValue.fromS("bcrypt-hash"));
        assertThat(request.item()).containsEntry(
                "createdAt",
                AttributeValue.fromS("2026-09-07T00:00:00Z"));
        assertThat(request.conditionExpression()).isEqualTo("attribute_not_exists(#email)");
        assertThat(request.expressionAttributeNames()).containsEntry("#email", "email");
    }

    @Test
    void saveReturnsFalseWhenTheEmailAlreadyExists() {
        recordingClient.failPutWithDuplicateEmail = true;

        boolean saved = repository.saveIfEmailAvailable(new User(
                "user_456",
                "user@example.com",
                "Another User",
                "bcrypt-hash",
                Instant.parse("2026-09-07T00:00:00Z")));

        assertThat(saved).isFalse();
    }

    private static final class RecordingDynamoDbClient {
        private GetItemResponse getItemResponse = GetItemResponse.builder().build();
        private GetItemRequest getItemRequest;
        private PutItemRequest putItemRequest;
        private boolean queryCalled;
        private boolean failPutWithDuplicateEmail;

        private DynamoDbClient client() {
            return (DynamoDbClient) Proxy.newProxyInstance(
                    DynamoDbClient.class.getClassLoader(),
                    new Class<?>[] {DynamoDbClient.class},
                    (proxy, method, arguments) -> {
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
                            if (failPutWithDuplicateEmail) {
                                throw ConditionalCheckFailedException.builder()
                                        .message("Email already exists")
                                        .build();
                            }
                            return null;
                        }
                        if (method.getName().equals("query")) {
                            queryCalled = true;
                            return null;
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
