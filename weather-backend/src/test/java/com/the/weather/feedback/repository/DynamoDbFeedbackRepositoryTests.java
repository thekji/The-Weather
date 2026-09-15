package com.the.weather.feedback.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.the.weather.feedback.model.FeedbackType;
import com.the.weather.feedback.model.PostFeedback;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

class DynamoDbFeedbackRepositoryTests {

    private static final Instant CREATED_AT = Instant.parse("2026-09-14T08:30:00Z");

    private DynamoDbClient dynamoDb;
    private DynamoDbFeedbackRepository repository;

    @BeforeEach
    void setUp() {
        dynamoDb = mock(DynamoDbClient.class);
        repository = new DynamoDbFeedbackRepository(
                dynamoDb, "PostFeedback", "CommunityPosts");
    }

    @Test
    void noneToHelpfulConditionallyCreatesFeedbackAndIncrementsHelpful() {
        mockCurrentAndCounts(null, 7, 1);

        var result = repository.set(feedback(FeedbackType.HELPFUL));

        TransactWriteItemsRequest request = capturedTransaction();
        assertThat(request.transactItems()).hasSize(2);
        var put = request.transactItems().getFirst().put();
        assertThat(put.tableName()).isEqualTo("PostFeedback");
        assertThat(put.item().get("userID").s()).isEqualTo("user_2");
        assertThat(put.item().get("feedbackType").s()).isEqualTo("HELPFUL");
        assertThat(put.conditionExpression()).contains("attribute_not_exists");

        var update = request.transactItems().get(1).update();
        assertThat(update.expressionAttributeNames().get("#counter"))
                .isEqualTo("helpfulCount");
        assertThat(result.myFeedback()).isEqualTo(FeedbackType.HELPFUL);
        assertThat(result.helpfulCount()).isEqualTo(7);
        assertThat(result.notHelpfulCount()).isEqualTo(1);
    }

    @Test
    void noneToNotHelpfulIncrementsOnlyNotHelpful() {
        mockCurrentAndCounts(null, 6, 2);

        var result = repository.set(feedback(FeedbackType.NOT_HELPFUL));

        var update = capturedTransaction().transactItems().get(1).update();
        assertThat(update.expressionAttributeNames().get("#counter"))
                .isEqualTo("notHelpfulCount");
        assertThat(result.myFeedback()).isEqualTo(FeedbackType.NOT_HELPFUL);
        assertThat(result.helpfulCount()).isEqualTo(6);
        assertThat(result.notHelpfulCount()).isEqualTo(2);
    }

    @Test
    void helpfulToNotHelpfulChangesFeedbackAndBothCountersAtomically() {
        mockCurrentAndCounts(FeedbackType.HELPFUL, 5, 3);

        var result = repository.set(feedback(FeedbackType.NOT_HELPFUL));

        TransactWriteItemsRequest request = capturedTransaction();
        var feedbackUpdate = request.transactItems().getFirst().update();
        assertThat(feedbackUpdate.updateExpression()).isEqualTo("SET #feedbackType = :next");
        assertThat(feedbackUpdate.expressionAttributeValues().get(":previous").s())
                .isEqualTo("HELPFUL");
        assertThat(feedbackUpdate.expressionAttributeValues().get(":next").s())
                .isEqualTo("NOT_HELPFUL");

        var counterUpdate = request.transactItems().get(1).update();
        assertThat(counterUpdate.updateExpression())
                .isEqualTo("ADD #previousCounter :minusOne, #nextCounter :one");
        assertThat(counterUpdate.expressionAttributeNames())
                .containsEntry("#previousCounter", "helpfulCount")
                .containsEntry("#nextCounter", "notHelpfulCount");
        assertThat(result.myFeedback()).isEqualTo(FeedbackType.NOT_HELPFUL);
    }

    @Test
    void notHelpfulToHelpfulChangesFeedbackAndBothCountersAtomically() {
        mockCurrentAndCounts(FeedbackType.NOT_HELPFUL, 7, 1);

        var result = repository.set(feedback(FeedbackType.HELPFUL));

        var counterUpdate = capturedTransaction().transactItems().get(1).update();
        assertThat(counterUpdate.expressionAttributeNames())
                .containsEntry("#previousCounter", "notHelpfulCount")
                .containsEntry("#nextCounter", "helpfulCount");
        assertThat(result.myFeedback()).isEqualTo(FeedbackType.HELPFUL);
    }

    @Test
    void removingHelpfulDeletesFeedbackAndDecrementsHelpfulAtomically() {
        mockCurrentAndCounts(FeedbackType.HELPFUL, 6, 1);

        var result = repository.remove("post_1", "user_2");

        TransactWriteItemsRequest request = capturedTransaction();
        assertThat(request.transactItems().getFirst().delete().conditionExpression())
                .isEqualTo("#feedbackType = :previous");
        var update = request.transactItems().get(1).update();
        assertThat(update.expressionAttributeNames().get("#counter"))
                .isEqualTo("helpfulCount");
        assertThat(update.expressionAttributeValues().get(":minusOne").n()).isEqualTo("-1");
        assertThat(result.myFeedback()).isNull();
        assertThat(result.helpfulCount()).isEqualTo(6);
    }

    @Test
    void settingSameFeedbackAndRemovingNoneAreIdempotent() {
        mockCurrentAndCounts(FeedbackType.HELPFUL, 7, 1);
        var unchanged = repository.set(feedback(FeedbackType.HELPFUL));

        assertThat(unchanged.myFeedback()).isEqualTo(FeedbackType.HELPFUL);
        verify(dynamoDb, never()).transactWriteItems(any(TransactWriteItemsRequest.class));

        dynamoDb = mock(DynamoDbClient.class);
        repository = new DynamoDbFeedbackRepository(
                dynamoDb, "PostFeedback", "CommunityPosts");
        mockCurrentAndCounts(null, 7, 1);
        var removed = repository.remove("post_1", "user_2");

        assertThat(removed.myFeedback()).isNull();
        verify(dynamoDb, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    void batchGetReturnsViewerFeedbackForFeedPosts() {
        when(dynamoDb.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(
                BatchGetItemResponse.builder()
                        .responses(Map.of("PostFeedback", List.of(
                                Map.of(
                                        "postID", AttributeValue.fromS("post_1"),
                                        "feedbackType", AttributeValue.fromS("HELPFUL")),
                                Map.of(
                                        "postID", AttributeValue.fromS("post_2"),
                                        "feedbackType", AttributeValue.fromS("NOT_HELPFUL")))))
                        .unprocessedKeys(Map.of())
                        .build());

        Map<String, FeedbackType> result = repository.findByPostIDsAndUserID(
                List.of("post_1", "post_2"), "user_2");

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                "post_1", FeedbackType.HELPFUL,
                "post_2", FeedbackType.NOT_HELPFUL));
        ArgumentCaptor<BatchGetItemRequest> captor =
                ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDb).batchGetItem(captor.capture());
        assertThat(captor.getValue().requestItems().get("PostFeedback").keys()).hasSize(2);
    }

    @Test
    void cleanupQueriesByPostPartitionAndDeletesEveryReturnedFeedbackWithoutScan() {
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(feedbackItem("user_2"), feedbackItem("user_3"))
                .lastEvaluatedKey(Map.of())
                .build());

        repository.deleteAllByPostID("post_1");

        ArgumentCaptor<QueryRequest> queryCaptor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDb).query(queryCaptor.capture());
        assertThat(queryCaptor.getValue().keyConditionExpression())
                .isEqualTo("#postID = :postID");
        ArgumentCaptor<DeleteItemRequest> deleteCaptor =
                ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDb, times(2)).deleteItem(deleteCaptor.capture());
        assertThat(deleteCaptor.getAllValues())
                .allSatisfy(delete -> assertThat(delete.key()).containsKeys("postID", "userID"));
        verify(dynamoDb, never()).scan(any(ScanRequest.class));
    }

    private void mockCurrentAndCounts(FeedbackType current, int helpful, int notHelpful) {
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest request = invocation.getArgument(0);
            if ("PostFeedback".equals(request.tableName())) {
                return GetItemResponse.builder()
                        .item(current == null
                                ? Map.of()
                                : Map.of("feedbackType", AttributeValue.fromS(current.name())))
                        .build();
            }
            return GetItemResponse.builder().item(Map.of(
                    "helpfulCount", AttributeValue.fromN(Integer.toString(helpful)),
                    "notHelpfulCount", AttributeValue.fromN(Integer.toString(notHelpful))))
                    .build();
        });
    }

    private TransactWriteItemsRequest capturedTransaction() {
        ArgumentCaptor<TransactWriteItemsRequest> captor =
                ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDb).transactWriteItems(captor.capture());
        return captor.getValue();
    }

    private static PostFeedback feedback(FeedbackType type) {
        return new PostFeedback("post_1", "user_2", type, CREATED_AT);
    }

    private static Map<String, AttributeValue> feedbackItem(String userID) {
        return Map.of(
                "postID", AttributeValue.fromS("post_1"),
                "userID", AttributeValue.fromS(userID),
                "feedbackType", AttributeValue.fromS("HELPFUL"),
                "createdAt", AttributeValue.fromS(CREATED_AT.toString()));
    }
}
