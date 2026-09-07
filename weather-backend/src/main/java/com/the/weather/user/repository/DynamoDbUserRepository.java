package com.the.weather.user.repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.the.weather.user.model.User;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Repository
public class DynamoDbUserRepository implements UserRepository {

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoDbUserRepository(
            DynamoDbClient dynamoDb,
            @Value("${dynamodb.users-table}") String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("email", AttributeValue.fromS(normalizedEmail)))
                .consistentRead(true)
                .build();

        Map<String, AttributeValue> item = dynamoDb.getItem(request).item();
        return item.isEmpty() ? Optional.empty() : Optional.of(toUser(item));
    }

    @Override
    public boolean saveIfEmailAvailable(User user) {
        Map<String, AttributeValue> item = Map.of(
                "userID", AttributeValue.fromS(user.userID()),
                "email", AttributeValue.fromS(user.email()),
                "name", AttributeValue.fromS(user.name()),
                "passwordHash", AttributeValue.fromS(user.passwordHash()),
                "createdAt", AttributeValue.fromS(user.createdAt().toString()));

        try {
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(#email)")
                    .expressionAttributeNames(Map.of("#email", "email"))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException exception) {
            return false;
        }
    }

    private static User toUser(Map<String, AttributeValue> item) {
        return new User(
                item.get("userID").s(),
                item.get("email").s(),
                item.get("name").s(),
                item.get("passwordHash").s(),
                Instant.parse(item.get("createdAt").s()));
    }
}
