package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_CANCELLED;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchError;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchSuccess;
import java.util.List;
import java.util.Objects;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel.TaskType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiBatchEmbeddingModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-embedding-001";

    @Nested
    class CreateBatchInline {

        @Test
        void should_create_batch_with_valid_text_segments() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Valid Embedding Requests";
            var priority = 1L;
            var textSegments = List.of(
                    TextSegment.from("The capital of France is Paris."),
                    TextSegment.from("France is a country in Europe."),
                    TextSegment.from("The capital of Germany is Berlin."),
                    TextSegment.from("Germany is known for its engineering."));

            // when
            var response = subject.createBatchInline(displayName, priority, textSegments);

            // then
            assertThat(response).isInstanceOf(BatchIncomplete.class);
            assertThat(((BatchIncomplete<?>) response).batchName().value()).startsWith("batches/");
            assertThat(((BatchIncomplete<?>) response).state()).isEqualTo(BATCH_STATE_PENDING);
        }

        @Test
        void should_create_batch_with_single_text_segment_list() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.SEMANTIC_SIMILARITY)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Single Request";
            var priority = 0L;
            var textSegments = List.of(TextSegment.from("This is a test document for embedding."));

            // when
            var response = subject.createBatchInline(displayName, priority, textSegments);

            // then
            assertThat(response).isInstanceOf(BatchIncomplete.class);
            assertThat(((BatchIncomplete<?>) response).batchName().value()).startsWith("batches/");
        }

        @Test
        void should_create_batch_with_different_task_types() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.CLUSTERING)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Clustering Task";
            var priority = 50L;
            var textSegments = List.of(
                    TextSegment.from("Document about machine learning"),
                    TextSegment.from("Document about artificial intelligence"),
                    TextSegment.from("Document about cooking recipes"),
                    TextSegment.from("Document about baking techniques"));

            // when
            var response = subject.createBatchInline(displayName, priority, textSegments);

            // then
            assertThat(response).isInstanceOf(BatchIncomplete.class);
            assertThat(((BatchIncomplete<?>) response).batchName().value()).startsWith("batches/");
        }

        @Test
        void should_create_batch_with_output_dimensionality() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_QUERY)
                    .outputDimensionality(256)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Custom Dimensionality";
            var priority = 0L;
            var textSegments = List.of(TextSegment.from("Query about embeddings with reduced dimensions"));

            // when
            var response = subject.createBatchInline(displayName, priority, textSegments);

            // then
            assertThat(response).isInstanceOf(BatchIncomplete.class);
            assertThat(((BatchIncomplete<?>) response).batchName().value()).startsWith("batches/");
        }
    }

    @Nested
    class CancelBatchJob {

        @Test
        void should_cancel_just_created_batch() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - To Cancel";
            var priority = 1L;
            var textSegments = List.of(
                    TextSegment.from("Text to embed 1"),
                    TextSegment.from("Text to embed 2"));
            BatchIncomplete<?> response =
                    (BatchIncomplete<?>) subject.createBatchInline(displayName, priority, textSegments);

            // when
            subject.cancelBatchJob(response.batchName());

            // then
            var retrieveResponse = subject.retrieveBatchResults(response.batchName());
            assertThat(retrieveResponse).isInstanceOf(BatchError.class);
            assertThat(((BatchError<?>) retrieveResponse).state()).isEqualTo(BATCH_STATE_CANCELLED);
            assertThat(((BatchError<?>) retrieveResponse).code()).isEqualTo(13);
        }

        @Test
        void should_throw_on_invalid_batch_name() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
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
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - To Delete";
            var priority = 1L;
            var textSegments = List.of(
                    TextSegment.from("Text to embed 1"),
                    TextSegment.from("Text to embed 2"));
            var response = (BatchIncomplete<?>) subject.createBatchInline(displayName, priority, textSegments);

            // when
            subject.deleteBatchJob(response.batchName());

            // then - no longer exists
            var list = subject.listBatchJobs(null, null);
            var batchNames = list.responses().stream()
                    .map(GoogleAiGeminiBatchEmbeddingModelIT::getBatchName)
                    .filter(Objects::nonNull)
                    .toList();
            assertThat(batchNames).doesNotContain(response.batchName());
        }

        @Test
        void should_throw_on_invalid_batch_name() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
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
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - To List";
            var priority = 1L;
            var textSegments = List.of(
                    TextSegment.from("Text to embed 1"),
                    TextSegment.from("Text to embed 2"));
            var createOperation = (BatchIncomplete<?>) subject.createBatchInline(displayName, priority, textSegments);

            // when
            var list = subject.listBatchJobs(null, null);
            var responses = list.responses();

            // then
            assertThat(responses).hasSizeGreaterThan(0);
            assertThat(((BatchIncomplete<?>) responses.get(0)).batchName()).isEqualTo(createOperation.batchName());
        }

        @Test
        void should_paginate_batches() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Pagination ";
            var priority = 1L;
            var textSegments = List.of(
                    TextSegment.from("Text to embed 1"),
                    TextSegment.from("Text to embed 2"));
            subject.createBatchInline(displayName + "1", priority, textSegments);
            subject.createBatchInline(displayName + "2", priority, textSegments);

            // when
            var list = subject.listBatchJobs(1, null);
            assertThat(list.responses()).hasSize(1);

            var secondList = subject.listBatchJobs(1, list.pageToken());
            assertThat(secondList.responses()).hasSize(1);
        }

        @Test
        void should_list_batches_with_custom_page_size() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
                    .logRequestsAndResponses(true)
                    .build();

            // when
            var list = subject.listBatchJobs(5, null);

            // then
            assertThat(list.responses()).hasSizeLessThanOrEqualTo(5);
        }
    }

    @Nested
    class RetrieveBatchResults {

        @Test
        void should_retrieve_pending_batch_status() {
            // given
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Retrieve Status";
            var priority = 1L;
            var textSegments = List.of(
                    TextSegment.from("Text to embed 1"),
                    TextSegment.from("Text to embed 2"));
            var createResponse = (BatchIncomplete<?>) subject.createBatchInline(displayName, priority, textSegments);

            // when
            var retrieveResponse = subject.retrieveBatchResults(createResponse.batchName());

            // then
            assertThat(retrieveResponse).isInstanceOf(BatchIncomplete.class);
            assertThat(((BatchIncomplete<?>) retrieveResponse).batchName()).isEqualTo(createResponse.batchName());
        }
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
