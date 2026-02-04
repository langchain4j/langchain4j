package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.batch.BatchJobState.CANCELLED;
import static dev.langchain4j.model.batch.BatchJobState.FAILED;
import static dev.langchain4j.model.batch.BatchJobState.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.batch.BatchName;
import dev.langchain4j.model.batch.BatchResponse;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriters;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiBatchChatModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");
    public static final String MODEL_NAME = "gemini-2.5-flash";

    @Test
    @Order(1)
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
        var response = subject.createBatch(displayName, priority, requests);

        // then
        assertThat(response.isIncomplete()).isTrue();
        assertThat(response.batchName().value()).startsWith("batches/");
        assertThat(response.state()).isEqualTo(PENDING);
    }

    @Test
    @Order(2)
    void should_write_upload_and_create_batch_from_file() throws Exception {
        // given
        var chatModel = GoogleAiGeminiBatchChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .logRequestsAndResponses(true)
                .build();

        var filesClient = GeminiFiles.builder().apiKey(GOOGLE_AI_GEMINI_API_KEY).build();

        var tempFile = Files.createTempFile("gemini-chat-it-test", ".jsonl");
        GeminiFiles.GeminiFile uploadedFile = null;
        BatchName batchName = null;

        try {
            // 1. Write batch requests to local temp file
            var requests = List.of(
                    new BatchFileRequest<>("req-1", createChatRequest("What is the speed of light?")),
                    new BatchFileRequest<>("req-2", createChatRequest("What is the speed of sound?")));

            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                chatModel.writeBatchToFile(writer, requests);
            }

            // 2. Upload the file to Google AI
            uploadedFile = filesClient.uploadFile(tempFile, "IT Chat Batch File");
            assertThat(uploadedFile.state()).isIn("ACTIVE");

            sleep();

            // 3. Create batch from the uploaded file
            var response = chatModel.createBatchFromFile("IT Chat File Batch", uploadedFile);

            // then
            assertThat(response.isIncomplete()).isTrue();
            assertThat(response.state()).isEqualTo(PENDING);
            batchName = response.batchName();
            assertThat(batchName.value()).startsWith("batches/");
        } finally {
            // Cleanup
            if (batchName != null) {
                try {
                    chatModel.cancelJob(batchName);
                } catch (Exception e) {
                    System.err.println("Failed to cancel batch job: " + e.getMessage());
                }
            }
            if (uploadedFile != null) {
                try {
                    filesClient.deleteFile(uploadedFile.name());
                } catch (Exception e) {
                    System.err.println("Failed to delete file: " + e.getMessage());
                }
            }
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @Order(3)
    void should_fail_to_create_batch_from_non_existent_file() {
        // given
        var chatModel = GoogleAiGeminiBatchChatModel.builder()
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
        assertThatThrownBy(() -> chatModel.createBatchFromFile("Bad Batch", nonExistentFile))
                .isInstanceOf(HttpException.class)
                .hasMessageContaining("Requested entity was not found");
    }

    @Test
    @Order(4)
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
        var response = subject.createBatch(displayName, priority, requests);

        // when
        subject.cancelJob(response.batchName());

        // then
        var retrieveResponse = subject.retrieveResults(response.batchName());
        assertThat(retrieveResponse.isError()).isTrue();
        assertThat(retrieveResponse.state()).isIn(CANCELLED, FAILED);
    }

    @Test
    @Order(5)
    void should_throw_on_invalid_batch_name_cancel() {
        // given
        var subject = GoogleAiGeminiBatchChatModel.builder()
                .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                .modelName(MODEL_NAME)
                .logRequestsAndResponses(true)
                .build();

        // when & then
        var batchName = new BatchName("batches/test-batch");
        assertThatThrownBy(() -> subject.cancelJob(batchName))
                .isInstanceOf(HttpException.class)
                .hasMessageContaining("\"message\": \"Could not parse the batch name\"");
    }

    @Test
    @Order(6)
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
        var response = subject.createBatch(displayName, priority, requests);

        // when
        subject.deleteBatchJob(response.batchName());

        // then - no longer exists
        var list = subject.listJobs(null, null);
        var batchNames = list.batches().stream().map(BatchResponse::batchName).toList();
        assertThat(batchNames).doesNotContain(response.batchName());
    }

    @Test
    @Order(7)
    void should_throw_on_invalid_batch_name_delete() {
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

    @Test
    @Order(8)
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
        var createResponse = subject.createBatch(displayName, priority, requests);

        // when
        var list = subject.listJobs(null, null);
        var batches = list.batches();
        assertThat(batches).hasSizeGreaterThan(0);
        assertThat(batches.get(0).batchName()).isEqualTo(createResponse.batchName());
    }

    @Test
    @Order(9)
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
        subject.createBatch(displayName + "1", priority, requests);
        subject.createBatch(displayName + "2", priority, requests);

        // when
        var list = subject.listJobs(1, null);
        assertThat(list.batches()).hasSize(1);

        var secondList = subject.listJobs(1, list.nextPageToken());
        assertThat(secondList.batches()).hasSize(1);
    }

    private static ChatRequest createChatRequest(String message) {
        return ChatRequest.builder()
                .modelName(MODEL_NAME)
                .messages(UserMessage.from(message))
                .build();
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
