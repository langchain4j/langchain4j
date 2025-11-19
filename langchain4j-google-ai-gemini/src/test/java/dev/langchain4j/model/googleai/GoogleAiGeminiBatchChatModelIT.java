package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_CANCELLED;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchError;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import java.util.List;
import java.util.Objects;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchSuccess;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiBatchChatModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    public static final String MODEL_NAME = "gemini-2.5-flash-lite";

    @Nested
    class CreateBatchInline {

        @Test
        void should_create_batch_with_valid_requests() {
            // given
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Valid Requests";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("What is the capital of France?"),
                    createChatRequest("What is the capital of Germany?"));

            // when
            var response = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(response).isInstanceOf(BatchIncomplete.class);
            assertThat(((BatchIncomplete<?>) response).batchName().value()).startsWith("batches/");
            assertThat(((BatchIncomplete<?>) response).state()).isEqualTo(BATCH_STATE_PENDING);
        }
    }

    @Nested
    class CancelBatchJob {

        @Test
        void should_cancel_just_created_batch() {
            // given
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Valid Requests";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("What is the capital of France?"),
                    createChatRequest("What is the capital of Germany?"));
            BatchIncomplete<?> response =
                    (BatchIncomplete<?>) subject.createBatchInline(displayName, priority, requests);

            // when
            subject.cancelBatchJob(response.batchName());

            // then
            var retrieveResponse = subject.retrieveBatchResults(
                    response.batchName()); // Retrieve the results to check cancelled state.
            assertThat(retrieveResponse).isInstanceOf(BatchError.class);
            assertThat(((BatchError<?>) retrieveResponse).state()).isEqualTo(BATCH_STATE_CANCELLED);
            assertThat(((BatchError<?>) retrieveResponse).code()).isEqualTo(13);
        }

        @Test
        void should_throw_on_invalid_batch_name() {
            // given
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .logRequestsAndResponses(true)
                    .build();

            // when & then
            var batchName = new BatchName("batches/test-batch");
            assertThatThrownBy(() -> subject.cancelBatchJob(batchName))
                    .isInstanceOf(HttpException.class)
                    .hasMessageContaining("\"message\": \"Could not parse the batch name\"");
        }
    }

    @Nested
    class DeleteBatchJob {
        @Test
        void should_delete_just_created_batch() {
            // given
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Valid Requests";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("What is the capital of France?"),
                    createChatRequest("What is the capital of Germany?"));
            var response = (BatchIncomplete<?>) subject.createBatchInline(displayName, priority, requests);

            // when
            subject.deleteBatchJob(response.batchName());

            // then - no longer exists
            var list = subject.listBatchJobs(null, null);
            var batchNames = list.responses().stream()
                    .map(GoogleAiGeminiBatchChatModelIT::getBatchName)
                    .filter(Objects::nonNull)
                    .toList();
            assertThat(batchNames).doesNotContain(response.batchName());
        }

        @Test
        void should_throw_on_invalid_batch_name() {
            // given
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .logRequestsAndResponses(true)
                    .build();

            // when & then
            var batchName = new BatchName("batches/test-batch");
            assertThatThrownBy(() -> subject.deleteBatchJob(batchName))
                    .isInstanceOf(HttpException.class)
                    .hasMessageContaining("\"message\": \"Could not parse the batch name\"");
        }
    }

    @Nested
    class ListBatches {
        @Test
        void should_list_just_created_batch() {
            // given
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Valid Requests";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("What is the capital of France?"),
                    createChatRequest("What is the capital of Germany?"));
            var createOperation = (BatchIncomplete<?>) subject.createBatchInline(displayName, priority, requests);

            // when
            var list = subject.listBatchJobs(null, null);
            var responses = list.responses();
            assertThat(responses).hasSizeGreaterThan(0);
            assertThat(((BatchIncomplete<?>) responses.get(0)).batchName()).isEqualTo(createOperation.batchName());
        }

        @Test
        void should_paginate_batches() {
            // given
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .logRequestsAndResponses(true)
                    .build();
            var displayName = "Test Batch - Valid Requests ";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("What is the capital of France?"),
                    createChatRequest("What is the capital of Germany?"));
            subject.createBatchInline(displayName + "1", priority, requests);
            subject.createBatchInline(displayName + "2", priority, requests);

            // when
            var list = subject.listBatchJobs(1, null);
            assertThat(list.responses()).hasSize(1);

            var secondList = subject.listBatchJobs(1, list.pageToken());
            assertThat(secondList.responses()).hasSize(1);
        }
    }

    private static ChatRequest createChatRequest(String message) {
        return ChatRequest.builder()
                .modelName(MODEL_NAME)
                .messages(UserMessage.from(message))
                .build();
    }

    private static @Nullable BatchName getBatchName(BatchResponse<?> res) {
        if (res instanceof BatchSuccess<?> success) {
            return success.batchName();
        } else if (res instanceof BatchIncomplete<?> pending) {
            return pending.batchName();
        } else if (res instanceof BatchError<?> error) {
            return error.batchName();
        } else {
            return null;
        }
    }
}
