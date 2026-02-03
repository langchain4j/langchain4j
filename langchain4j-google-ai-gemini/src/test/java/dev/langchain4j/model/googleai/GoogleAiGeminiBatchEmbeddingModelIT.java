package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.batch.BatchJobState.BATCH_STATE_CANCELLED;
import static dev.langchain4j.model.batch.BatchJobState.BATCH_STATE_FAILED;
import static dev.langchain4j.model.batch.BatchJobState.BATCH_STATE_PENDING;
import static java.nio.file.Files.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.batch.BatchName;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel.TaskType;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriters;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiBatchEmbeddingModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    private static final String MODEL_NAME = "gemini-embedding-001";

    @Nested
    class CreateBatch {

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
            var response = subject.createBatch(displayName, priority, textSegments);

            // then
            assertThat(response.isIncomplete()).isTrue();
            assertThat(response.batchName().value()).startsWith("batches/");
            assertThat(response.state()).isEqualTo(BATCH_STATE_PENDING);
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
            var response = subject.createBatch(displayName, priority, textSegments);

            // then
            assertThat(response.isIncomplete()).isTrue();
            assertThat(response.batchName().value()).startsWith("batches/");
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
            var response = subject.createBatch(displayName, priority, textSegments);

            // then
            assertThat(response.isIncomplete()).isTrue();
            assertThat(response.batchName().value()).startsWith("batches/");
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
            var response = subject.createBatch(displayName, priority, textSegments);

            // then
            assertThat(response.isIncomplete()).isTrue();
            assertThat(response.batchName().value()).startsWith("batches/");
        }
    }

    @Nested
    class BatchFromFile {

        @Test
        void should_write_upload_and_create_batch_from_file() throws Exception {
            // given
            var embeddingModel = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .taskType(TaskType.RETRIEVAL_DOCUMENT)
                    .logRequestsAndResponses(true)
                    .build();

            var filesClient =
                    GeminiFiles.builder().apiKey(GOOGLE_AI_GEMINI_API_KEY).build();

            var tempFile = createTempFile("gemini-it-test", ".jsonl");
            GeminiFiles.GeminiFile uploadedFile;
            BatchName batchName;

            // 1. Write batch requests to local temp file (Integration of writeBatchToFile)
            var requests = List.of(
                    new BatchFileRequest<>("req-1", TextSegment.from("Integration test segment 1")),
                    new BatchFileRequest<>("req-2", TextSegment.from("Integration test segment 2")));

            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                embeddingModel.writeBatchToFile(writer, requests);
            }

            // 2. Upload the file to Google AI (Prerequisite for createBatchFromFile)
            uploadedFile = filesClient.uploadFile(tempFile, "IT Batch File");
            System.out.println("Got: " + uploadedFile);
            assertThat(uploadedFile.state()).isEqualTo("ACTIVE");

            // 3. Create batch from the uploaded file (Integration of createBatchFromFile)
            var response = embeddingModel.createBatchFromFile("IT File Batch", uploadedFile);

            // then
            assertThat(response.isIncomplete()).isTrue();
            assertThat(response.state()).isEqualTo(BATCH_STATE_PENDING);
            batchName = response.batchName();
            assertThat(batchName.value()).startsWith("batches/");
        }

        @Test
        void should_fail_to_create_batch_from_non_existent_file() {
            // given
            var embeddingModel = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName(MODEL_NAME)
                    .build();

            var nonExistentFile = new GeminiFiles.GeminiFile(
                    "files/1234567890",
                    "Fake File",
                    "text/plain",
                    0L,
                    "2025-01-01T00:00:00Z",
                    "2025-01-01T00:00:00Z",
                    "2025-01-03T00:00:00Z",
                    "hash",
                    "https://uri",
                    "ACTIVE");

            // when & then
            assertThatThrownBy(() -> embeddingModel.createBatchFromFile("Bad Batch", nonExistentFile))
                    .isInstanceOf(HttpException.class)
                    .hasMessageContaining("Requested entity was not found");
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
            var textSegments = List.of(TextSegment.from("Text to embed 1"), TextSegment.from("Text to embed 2"));
            var response = subject.createBatch(displayName, priority, textSegments);

            // when
            subject.cancelBatchJob(response.batchName());

            // then
            var retrieveResponse = subject.retrieveBatchResults(response.batchName());
            assertThat(retrieveResponse.isError()).isTrue();
            assertThat(retrieveResponse.state()).isIn(BATCH_STATE_CANCELLED, BATCH_STATE_FAILED);
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
            var textSegments = List.of(TextSegment.from("Text to embed 1"), TextSegment.from("Text to embed 2"));
            var response = subject.createBatch(displayName, priority, textSegments);

            // when
            subject.deleteBatchJob(response.batchName());

            // then - no longer exists
            var list = subject.listBatchJobs(null, null);
            var batchNames =
                    list.batches().stream().map(BatchResponse::batchName).toList();
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
            var textSegments = List.of(TextSegment.from("Text to embed 1"), TextSegment.from("Text to embed 2"));
            var createResponse = subject.createBatch(displayName, priority, textSegments);

            // when
            var list = subject.listBatchJobs(null, null);
            var batches = list.batches();

            // then
            assertThat(batches).hasSizeGreaterThan(0);
            assertThat(batches.get(0).batchName()).isEqualTo(createResponse.batchName());
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
            var textSegments = List.of(TextSegment.from("Text to embed 1"), TextSegment.from("Text to embed 2"));
            subject.createBatch(displayName + "1", priority, textSegments);
            subject.createBatch(displayName + "2", priority, textSegments);

            // when
            var list = subject.listBatchJobs(1, null);
            assertThat(list.batches()).hasSize(1);

            var secondList = subject.listBatchJobs(1, list.nextPageToken());
            assertThat(secondList.batches()).hasSize(1);
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
            assertThat(list.batches()).hasSizeLessThanOrEqualTo(5);
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
            var textSegments = List.of(TextSegment.from("Text to embed 1"), TextSegment.from("Text to embed 2"));
            var createResponse = subject.createBatch(displayName, priority, textSegments);

            // when
            var retrieveResponse = subject.retrieveBatchResults(createResponse.batchName());

            // then
            assertThat(retrieveResponse.isIncomplete()).isTrue();
            assertThat(retrieveResponse.batchName()).isEqualTo(createResponse.batchName());
        }
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        sleep();
    }

    private static void sleep() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI_BATCH");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
