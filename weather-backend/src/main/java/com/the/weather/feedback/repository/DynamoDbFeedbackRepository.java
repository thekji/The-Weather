package com.the.weather.feedback.repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.the.weather.feedback.model.FeedbackType;
import com.the.weather.feedback.model.PostFeedback;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

@Repository
public class DynamoDbFeedbackRepository implements FeedbackRepository {

    private static final int TRANSACTION_ATTEMPTS = 3;
    private static final int BATCH_GET_ATTEMPTS = 3;
    private static final String POST_ID = "postID";
    private static final String USER_ID = "userID";
    private static final String FEEDBACK_TYPE = "feedbackType";

    private final DynamoDbClient dynamoDb;
    private final String feedbackTableName;
    private final String postsTableName;

    public DynamoDbFeedbackRepository(
            DynamoDbClient dynamoDb,
            @Value("${dynamodb.post-feedback-table}") String feedbackTableName,
            @Value("${dynamodb.community-posts-table}") String postsTableName) {
        this.dynamoDb = dynamoDb;
        this.feedbackTableName = feedbackTableName;
        this.postsTableName = postsTableName;
    }

    @Override
    public FeedbackStateResult set(PostFeedback feedback) {
        return retryOnConcurrentChange(() -> setOnce(feedback));
    }

    @Override
    public FeedbackStateResult remove(String postID, String userID) {
        return retryOnConcurrentChange(() -> removeOnce(postID, userID));
    }

    @Override
    public Map<String, FeedbackType> findByPostIDsAndUserID(
            List<String> postIDs,
            String userID) {
        if (postIDs == null || postIDs.isEmpty() || userID == null || userID.isBlank()) {
            return Map.of();
        }

        List<Map<String, AttributeValue>> keys = new LinkedHashSet<>(postIDs).stream()
                .map(postID -> feedbackKey(postID, userID))
                .toList();
        Map<String, KeysAndAttributes> requested = Map.of(
                feedbackTableName,
                KeysAndAttributes.builder()
                        .keys(keys)
                        .consistentRead(true)
                        .projectionExpression(POST_ID + ", " + FEEDBACK_TYPE)
                        .build());
        List<Map<String, AttributeValue>> items = new ArrayList<>();

        for (int attempt = 0; attempt < BATCH_GET_ATTEMPTS && !requested.isEmpty(); attempt++) {
            var response = dynamoDb.batchGetItem(BatchGetItemRequest.builder()
                    .requestItems(requested)
                    .build());
            items.addAll(response.responses().getOrDefault(feedbackTableName, List.of()));
            requested = response.unprocessedKeys();
        }
        if (!requested.isEmpty()) {
            throw new IllegalStateException("Feedback state could not be loaded completely.");
        }

        return items.stream().collect(Collectors.toUnmodifiableMap(
                item -> item.get(POST_ID).s(),
                item -> FeedbackType.valueOf(item.get(FEEDBACK_TYPE).s())));
    }

    @Override
    public void deleteAllByPostID(String postID) {
        Map<String, AttributeValue> lastEvaluatedKey = Map.of();

        do {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(feedbackTableName)
                    .keyConditionExpression("#postID = :postID")
                    .expressionAttributeNames(Map.of("#postID", POST_ID))
                    .expressionAttributeValues(Map.of(
                            ":postID", AttributeValue.fromS(postID)))
                    .consistentRead(true);
            if (!lastEvaluatedKey.isEmpty()) {
                request.exclusiveStartKey(lastEvaluatedKey);
            }

            QueryResponse response = dynamoDb.query(request.build());
            for (Map<String, AttributeValue> item : response.items()) {
                dynamoDb.deleteItem(DeleteItemRequest.builder()
                        .tableName(feedbackTableName)
                        .key(Map.of(
                                POST_ID, item.get(POST_ID),
                                USER_ID, item.get(USER_ID)))
                        .build());
            }
            lastEvaluatedKey = response.lastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
    }

    private FeedbackStateResult setOnce(PostFeedback feedback) {
        Optional<FeedbackType> current = currentFeedback(feedback.postID(), feedback.userID());
        if (current.filter(feedback.feedbackType()::equals).isPresent()) {
            return state(feedback.postID(), feedback.feedbackType());
        }

        TransactWriteItem feedbackWrite = current
                .map(previous -> updateFeedback(feedback, previous))
                .orElseGet(() -> putFeedback(feedback));
        TransactWriteItem counterWrite = current
                .map(previous -> switchCounters(feedback.postID(), previous, feedback.feedbackType()))
                .orElseGet(() -> incrementCounter(feedback.postID(), feedback.feedbackType()));

        transact(feedbackWrite, counterWrite);
        return state(feedback.postID(), feedback.feedbackType());
    }

    private FeedbackStateResult removeOnce(String postID, String userID) {
        Optional<FeedbackType> current = currentFeedback(postID, userID);
        if (current.isEmpty()) return state(postID, null);

        FeedbackType previous = current.get();
        Delete deleteFeedback = Delete.builder()
                .tableName(feedbackTableName)
                .key(feedbackKey(postID, userID))
                .conditionExpression("#feedbackType = :previous")
                .expressionAttributeNames(Map.of("#feedbackType", FEEDBACK_TYPE))
                .expressionAttributeValues(Map.of(
                        ":previous", AttributeValue.fromS(previous.name())))
                .build();
        Update decrementCounter = Update.builder()
                .tableName(postsTableName)
                .key(postKey(postID))
                .updateExpression("ADD #counter :minusOne")
                .conditionExpression("attribute_exists(#postID) AND #counter >= :one")
                .expressionAttributeNames(Map.of(
                        "#postID", POST_ID,
                        "#counter", counter(previous)))
                .expressionAttributeValues(Map.of(
                        ":minusOne", AttributeValue.fromN("-1"),
                        ":one", AttributeValue.fromN("1")))
                .build();

        transact(
                TransactWriteItem.builder().delete(deleteFeedback).build(),
                TransactWriteItem.builder().update(decrementCounter).build());
        return state(postID, null);
    }

    private TransactWriteItem putFeedback(PostFeedback feedback) {
        Put put = Put.builder()
                .tableName(feedbackTableName)
                .item(Map.of(
                        POST_ID, AttributeValue.fromS(feedback.postID()),
                        USER_ID, AttributeValue.fromS(feedback.userID()),
                        FEEDBACK_TYPE, AttributeValue.fromS(feedback.feedbackType().name()),
                        "createdAt", AttributeValue.fromS(feedback.createdAt().toString())))
                .conditionExpression("attribute_not_exists(#postID) AND attribute_not_exists(#userID)")
                .expressionAttributeNames(Map.of(
                        "#postID", POST_ID,
                        "#userID", USER_ID))
                .build();
        return TransactWriteItem.builder().put(put).build();
    }

    private TransactWriteItem updateFeedback(PostFeedback feedback, FeedbackType previous) {
        Update update = Update.builder()
                .tableName(feedbackTableName)
                .key(feedbackKey(feedback.postID(), feedback.userID()))
                .updateExpression("SET #feedbackType = :next")
                .conditionExpression("#feedbackType = :previous")
                .expressionAttributeNames(Map.of("#feedbackType", FEEDBACK_TYPE))
                .expressionAttributeValues(Map.of(
                        ":previous", AttributeValue.fromS(previous.name()),
                        ":next", AttributeValue.fromS(feedback.feedbackType().name())))
                .build();
        return TransactWriteItem.builder().update(update).build();
    }

    private TransactWriteItem incrementCounter(String postID, FeedbackType type) {
        Update update = Update.builder()
                .tableName(postsTableName)
                .key(postKey(postID))
                .updateExpression("ADD #counter :one")
                .conditionExpression("attribute_exists(#postID)")
                .expressionAttributeNames(Map.of(
                        "#postID", POST_ID,
                        "#counter", counter(type)))
                .expressionAttributeValues(Map.of(":one", AttributeValue.fromN("1")))
                .build();
        return TransactWriteItem.builder().update(update).build();
    }

    private TransactWriteItem switchCounters(
            String postID,
            FeedbackType previous,
            FeedbackType next) {
        Update update = Update.builder()
                .tableName(postsTableName)
                .key(postKey(postID))
                .updateExpression("ADD #previousCounter :minusOne, #nextCounter :one")
                .conditionExpression(
                        "attribute_exists(#postID) AND #previousCounter >= :one")
                .expressionAttributeNames(Map.of(
                        "#postID", POST_ID,
                        "#previousCounter", counter(previous),
                        "#nextCounter", counter(next)))
                .expressionAttributeValues(Map.of(
                        ":minusOne", AttributeValue.fromN("-1"),
                        ":one", AttributeValue.fromN("1")))
                .build();
        return TransactWriteItem.builder().update(update).build();
    }

    private Optional<FeedbackType> currentFeedback(String postID, String userID) {
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(feedbackTableName)
                .key(feedbackKey(postID, userID))
                .consistentRead(true)
                .projectionExpression(FEEDBACK_TYPE)
                .build()).item();
        AttributeValue value = item.get(FEEDBACK_TYPE);
        return value == null || value.s() == null
                ? Optional.empty()
                : Optional.of(FeedbackType.valueOf(value.s()));
    }

    private FeedbackStateResult state(String postID, FeedbackType myFeedback) {
        Map<String, AttributeValue> post = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(postsTableName)
                .key(postKey(postID))
                .consistentRead(true)
                .projectionExpression("helpfulCount, notHelpfulCount")
                .build()).item();
        return new FeedbackStateResult(
                myFeedback,
                count(post, "helpfulCount"),
                count(post, "notHelpfulCount"));
    }

    private void transact(TransactWriteItem... items) {
        dynamoDb.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(items)
                .build());
    }

    private FeedbackStateResult retryOnConcurrentChange(Supplier<FeedbackStateResult> operation) {
        TransactionCanceledException lastFailure = null;
        for (int attempt = 0; attempt < TRANSACTION_ATTEMPTS; attempt++) {
            try {
                return operation.get();
            } catch (TransactionCanceledException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }

    private static String counter(FeedbackType type) {
        return type == FeedbackType.HELPFUL ? "helpfulCount" : "notHelpfulCount";
    }

    private static Map<String, AttributeValue> feedbackKey(String postID, String userID) {
        return Map.of(
                POST_ID, AttributeValue.fromS(postID),
                USER_ID, AttributeValue.fromS(userID));
    }

    private static Map<String, AttributeValue> postKey(String postID) {
        return Map.of(POST_ID, AttributeValue.fromS(postID));
    }

    private static int count(Map<String, AttributeValue> item, String name) {
        AttributeValue value = item.get(name);
        return value == null || value.n() == null ? 0 : Integer.parseInt(value.n());
    }
}
