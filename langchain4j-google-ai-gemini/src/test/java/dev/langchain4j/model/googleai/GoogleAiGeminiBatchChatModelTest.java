package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_CANCELLED;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_FAILED;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_PENDING;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_RUNNING;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_SUCCEEDED;
import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.BATCH_GENERATE_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchError;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchList;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchSuccess;
import dev.langchain4j.model.googleai.BatchRequestResponse.ListOperationsResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.Operation;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUsageMetadata;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriters;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiBatchChatModelTest {

    private static final String MODEL_NAME = "gemini-2.5-flash-lite";

    @Mock
    private GeminiService mockGeminiService;

    @Captor
    private ArgumentCaptor<BatchCreateRequest<GeminiGenerateContentRequest>> batchRequestCaptor;

    private GoogleAiGeminiBatchChatModel subject;

    @BeforeEach
    void setUp() {
        subject = createSubject();
    }

    @Nested
    class CreateBatchInline {
        @Test
        void should_create_batch_with_valid_requests() {
            // given
            var displayName = "Test Batch";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest(MODEL_NAME, "What is the capital of France?"),
                    createChatRequest(MODEL_NAME, "What is the capital of Finland?"),
                    createChatRequest(MODEL_NAME, "What is the capital of Germany?"));
            var expectedOperation = createPendingOperation("batches/test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-123"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isEqualTo(priority);
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(3);
        }

        @Test
        void should_create_batch_with_null_priority_defaulting_to_zero() {
            // given
            var displayName = "Test Batch";
            var requests = List.of(ChatRequest.builder()
                    .messages(UserMessage.from("What is the capital of Italy?"))
                    .build());
            var expectedOperation = createPendingOperation("batches/test-456", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, null, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-456"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isZero();
        }

        @Test
        void should_create_batch_with_single_request() {
            // given
            var displayName = "Single Request Batch";
            var priority = 5L;
            var requests = List.of(createChatRequest(MODEL_NAME, "Explain quantum computing"));
            var expectedOperation = createPendingOperation("batches/test-789", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-789"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(1);
        }

        @Test
        void should_throw_exception_when_requests_have_different_models() {
            // given
            var displayName = "Mixed Models Batch";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("gemini-2.5-flash", "Question 1"),
                    createChatRequest("gemini-2.5-pro", "Question 2"));

            // when & then
            assertThatThrownBy(() -> subject.createBatchInline(displayName, priority, requests))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Batch requests cannot contain ChatRequest objects with different models");
        }

        @Test
        void should_create_batch_with_negative_priority() {
            // given
            var displayName = "Low Priority Batch";
            var priority = -10L;
            var requests = List.of(createChatRequest(MODEL_NAME, "What is AI?"));
            var expectedOperation = createPendingOperation("batches/test-negative", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-negative"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().priority()).isEqualTo(-10L);
        }

        @Test
        void should_extract_correct_model_name_from_requests() {
            // given
            var displayName = "Model Extraction Test";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest(MODEL_NAME, "Question 1"),
                    createChatRequest(MODEL_NAME, "Question 2"),
                    createChatRequest(MODEL_NAME, "Question 3"));
            var expectedOperation = createPendingOperation("batches/test-model", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);

            verify(mockGeminiService)
                    .batchCreate(
                            eq(MODEL_NAME),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT));
        }

        @Test
        void should_wrap_requests_in_inlined_request_with_empty_metadata() {
            // given
            var displayName = "Metadata Test";
            var priority = 1L;
            var requests = List.of(createChatRequest(MODEL_NAME, "Test message"));
            var expectedOperation = createPendingOperation("batches/test-metadata", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);

            verify(mockGeminiService).batchCreate(any(), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            var inlinedRequests =
                    capturedRequest.batch().inputConfig().requests().requests();
            assertThat(inlinedRequests).hasSize(1);
            assertThat(inlinedRequests.get(0).metadata()).isEmpty();
        }

        @Test
        void should_create_batch_with_multiple_requests() {
            // given
            var displayName = "Multiple Requests Batch";
            var priority = 2L;
            var requests = List.of(
                    createChatRequest(MODEL_NAME, "Question 1"),
                    createChatRequest(MODEL_NAME, "Question 2"),
                    createChatRequest(MODEL_NAME, "Question 3"),
                    createChatRequest(MODEL_NAME, "Question 4"),
                    createChatRequest(MODEL_NAME, "Question 5"));
            var expectedOperation = createPendingOperation("batches/test-multiple", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-multiple"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(5);
        }
    }

    @Nested
    class CreateBatchFromFile {
        @Captor
        private ArgumentCaptor<BatchCreateFileRequest> batchRequestCaptor;

        @Mock
        private GeminiFile mockGeminiFile;

        @Test
        void should_create_batch_from_file_with_valid_parameters() {
            // given
            String displayName = "Batch from File";
            when(mockGeminiFile.name()).thenReturn("files/test-file-123");
            var expectedOperation = createPendingOperation("batches/chat-file-test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchFromFile(displayName, mockGeminiFile);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/chat-file-test-123"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().inputConfig().fileName()).isEqualTo("files/test-file-123");
        }

        @Test
        void should_create_batch_from_file_with_null_priority() {
            // given
            String displayName = "Batch from File with Null Priority";
            when(mockGeminiFile.name()).thenReturn("files/test-file-456");
            var expectedOperation = createPendingOperation("batches/chat-file-test-456", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchFromFile(displayName, mockGeminiFile);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/chat-file-test-456"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().inputConfig().fileName()).isEqualTo("files/test-file-456");
        }

        @Test
        void should_throw_exception_when_creating_batch_from_file_fails() {
            // given
            String displayName = "Batch from File";
            when(mockGeminiFile.name()).thenReturn("files/test-file-error");
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(BATCH_GENERATE_CONTENT)))
                    .thenThrow(new RuntimeException("Error creating batch from file"));

            // when & then
            assertThatThrownBy(() -> subject.createBatchFromFile(displayName, mockGeminiFile))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error creating batch from file");
        }

        @Test
        void should_use_correct_model_name_when_creating_batch_from_file() {
            // given
            String displayName = "Model Name Test";
            when(mockGeminiFile.name()).thenReturn("files/test-file-model");
            var expectedOperation = createPendingOperation("batches/chat-file-model", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            subject.createBatchFromFile(displayName, mockGeminiFile);

            // then
            verify(mockGeminiService)
                    .<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(BATCH_GENERATE_CONTENT));
        }
    }

    @Nested
    class WriteBatchToFile {
        private Path tempFile;

        @BeforeEach
        void setUp() throws IOException {
            tempFile = Files.createTempFile("testBatchFile", ".jsonl");
        }

        @AfterEach
        void tearDown() throws IOException {
            Files.deleteIfExists(tempFile);
        }

        @Test
        void should_write_single_request_to_file() throws Exception {
            // given
            var chatRequest = createChatRequest(MODEL_NAME, "Sample question");
            var request = new BatchFileRequest<>("key-1", chatRequest);
            var requests = List.of(request);

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiGenerateContentRequest> writtenRequests = readRequestsFromFile(tempFile);
            var capturedRequest = writtenRequests.get(0);
            assertThat(capturedRequest.contents()).hasSize(1);
            assertThat(capturedRequest.contents().get(0).parts().get(0).text()).isEqualTo("Sample question");
        }

        @Test
        void should_write_multiple_requests_to_file() throws Exception {
            // given
            var chatRequest1 = createChatRequest(MODEL_NAME, "First question");
            var chatRequest2 = createChatRequest(MODEL_NAME, "Second question");
            var chatRequest3 = createChatRequest(MODEL_NAME, "Third question");
            var requests = List.of(
                    new BatchFileRequest<>("key-1", chatRequest1),
                    new BatchFileRequest<>("key-2", chatRequest2),
                    new BatchFileRequest<>("key-3", chatRequest3));

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiGenerateContentRequest> writtenRequests = readRequestsFromFile(tempFile);
            assertThat(writtenRequests).hasSize(3);
            assertThat(writtenRequests.get(0).contents().get(0).parts().get(0).text())
                    .isEqualTo("First question");
            assertThat(writtenRequests.get(1).contents().get(0).parts().get(0).text())
                    .isEqualTo("Second question");
            assertThat(writtenRequests.get(2).contents().get(0).parts().get(0).text())
                    .isEqualTo("Third question");
        }

        @Test
        void should_handle_empty_requests_list() throws Exception {
            // given
            List<BatchFileRequest<ChatRequest>> requests = List.of();

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiGenerateContentRequest> writtenRequests = readRequestsFromFile(tempFile);
            assertThat(writtenRequests).isEmpty();
        }

        @Test
        void should_write_requests_with_different_text_lengths() throws Exception {
            // given
            var shortRequest = createChatRequest(MODEL_NAME, "Short");
            var mediumRequest = createChatRequest(MODEL_NAME, "This is a medium length question for testing");
            var longRequest = createChatRequest(
                    MODEL_NAME,
                    "This is a very long question that contains much more content "
                            + "and is designed to test how the writer handles larger text inputs "
                            + "with multiple sentences and various punctuation marks.");
            var requests = List.of(
                    new BatchFileRequest<>("key-1", shortRequest),
                    new BatchFileRequest<>("key-2", mediumRequest),
                    new BatchFileRequest<>("key-3", longRequest));

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiGenerateContentRequest> writtenRequests = readRequestsFromFile(tempFile);
            assertThat(writtenRequests).hasSize(3);
            assertThat(writtenRequests.get(0).contents().get(0).parts().get(0).text())
                    .isEqualTo("Short");
            assertThat(writtenRequests.get(1).contents().get(0).parts().get(0).text())
                    .isEqualTo("This is a medium length question for testing");
            assertThat(writtenRequests.get(2).contents().get(0).parts().get(0).text())
                    .contains("very long question");
        }

        private List<GeminiGenerateContentRequest> readRequestsFromFile(Path file) throws IOException {
            List<GeminiGenerateContentRequest> requests = new ArrayList<>();
            ObjectMapper testMapper = new ObjectMapper();

            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    BatchFileRequest<GeminiGenerateContentRequest> batchRequest =
                            testMapper.readValue(line, new TypeReference<>() {});

                    requests.add(batchRequest.request());
                }
            }
            return requests;
        }
    }

    @Nested
    class RetrieveBatchResults {

        @Test
        void should_throw_when_invalid_batch_name() {
            assertThatThrownBy(() -> new BatchName("test-pending"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Batch name must start with 'batches/'");
        }

        @Test
        void should_return_pending_when_batch_is_still_processing() {
            // given
            var batchName = new BatchName("batches/test-pending");
            var pendingOperation = createPendingOperation("batches/test-pending", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(pendingOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(batchName, BATCH_STATE_PENDING));
        }

        @Test
        void should_return_pending_when_batch_is_running() {
            // given
            var batchName = new BatchName("batches/test-running");
            var runningOperation = createPendingOperation("batches/test-running", BATCH_STATE_RUNNING);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(runningOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(batchName, BATCH_STATE_RUNNING));
        }

        @Test
        void should_return_success_when_batch_processing_is_completed() {
            // given
            var batchName = new BatchName("batches/test-success");
            var chatResponses = List.of(createChatResponse("Response 1"), createChatResponse("Response 2"));
            var successOperation = createSuccessOperation("batches/test-success", chatResponses);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<ChatResponse>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEqualTo(chatResponses);
        }

        @Test
        void should_return_success_with_empty_responses_when_response_is_null() {
            // given
            var batchName = new BatchName("batches/test-empty");
            var successOperation = createSuccessOperationWithNullResponse("batches/test-empty");
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<ChatResponse>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEmpty();
        }

        @Test
        void should_return_error_when_batch_processing_is_cancelled() {
            // given
            var batchName = new BatchName("batches/test-error");
            var errorOperation =
                    createCancelledOperation("batches/test-error", "batches/test-error failed without error");
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(new BatchError<>(
                            batchName,
                            13,
                            "batches/test-error failed without error",
                            BATCH_STATE_CANCELLED,
                            List.of()));
        }

        @Test
        void should_return_error_when_batch_processing_fails() {
            // given
            var batchName = new BatchName("batches/test-error");
            var errorOperation = createErrorOperation("batches/test-error", 404, "Not Found", List.of());
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(new BatchError<>(batchName, 404, "Not Found", BATCH_STATE_FAILED, List.of()));
        }

        @Test
        void should_return_error_with_details_when_batch_processing_fails_with_details() {
            // given
            var batchName = new BatchName("batches/test-error-details");
            List<Map<String, Object>> errorDetails = List.of(
                    Map.of("@type", "type.googleapis.com/google.rpc.ErrorInfo", "reason", "INVALID_ARGUMENT"),
                    Map.of("@type", "type.googleapis.com/google.rpc.BadRequest", "field", "model"));
            var errorOperation = createErrorOperation("batches/test-error-details", 400, "Bad Request", errorDetails);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(new BatchError<>(batchName, 400, "Bad Request", BATCH_STATE_FAILED, errorDetails));
        }

        @Test
        void should_return_pending_with_unspecified_state_when_metadata_is_null() {
            // given
            var batchName = new BatchName("batches/test-no-metadata");
            var operation =
                    new Operation<GeminiGenerateContentResponse>("batches/test-no-metadata", null, false, null, null);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(operation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(batchName, BatchJobState.UNSPECIFIED));
        }

        @Test
        void should_return_pending_with_unspecified_state_when_state_is_missing_from_metadata() {
            // given
            var batchName = new BatchName("batches/test-no-state");
            var operation = new Operation<GeminiGenerateContentResponse>(
                    "batches/test-no-state", Map.of("createTime", "2025-10-23T09:26:30Z"), false, null, null);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(operation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(batchName, BatchJobState.UNSPECIFIED));
        }

        @Test
        void should_return_success_with_single_response() {
            // given
            var batchName = new BatchName("batches/test-single");
            var chatResponses = List.of(createChatResponse("Single response"));
            var successOperation = createSuccessOperation("batches/test-single", chatResponses);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<ChatResponse>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEqualTo(chatResponses);
        }

        @Test
        void should_return_success_with_multiple_responses() {
            // given
            var batchName = new BatchName("batches/test-multiple");
            var chatResponses = List.of(
                    createChatResponse("Response 1"),
                    createChatResponse("Response 2"),
                    createChatResponse("Response 3"),
                    createChatResponse("Response 4"),
                    createChatResponse("Response 5"));
            var successOperation = createSuccessOperation("batches/test-multiple", chatResponses);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<ChatResponse>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEqualTo(chatResponses);
        }
    }

    @Nested
    class CancelBatchJob {
        @ParameterizedTest
        @CsvSource({
            "batches/test-cannot-cancel, Batch cannot be cancelled because it has already completed",
            "batches/test-already-cancelled, Batch is already in CANCELLED state",
            "batches/non-existent, Batch not found"
        })
        void should_throw_exception_when_batch_cancellation_fails(String batchNameValue, String errorMessage) {
            // given
            var batchName = new BatchName(batchNameValue);
            when(mockGeminiService.batchCancelBatch(batchName.value())).thenThrow(new RuntimeException(errorMessage));

            // when & then
            assertThatThrownBy(() -> subject.cancelBatchJob(batchName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);
        }

        @Test
        void should_cancel_pending_batch() {
            // given
            var batchName = new BatchName("batches/test-pending-cancel");

            // when
            subject.cancelBatchJob(batchName);

            // then
            verify(mockGeminiService).batchCancelBatch("batches/test-pending-cancel");
        }

        @Test
        void should_cancel_running_batch() {
            // given
            var batchName = new BatchName("batches/test-running-cancel");

            // when
            subject.cancelBatchJob(batchName);

            // then
            verify(mockGeminiService).batchCancelBatch("batches/test-running-cancel");
        }
    }

    @Nested
    class DeleteBatchJob {

        @Test
        void should_delete_batch() {
            // given
            var batchName = new BatchName("batches/test-completed-delete");

            // when
            subject.deleteBatchJob(batchName);

            // then
            verify(mockGeminiService).batchDeleteBatch("batches/test-completed-delete");
        }

        @ParameterizedTest
        @CsvSource({
            "batches/test-cannot-delete, Batch cannot be deleted due to server error",
            "batches/non-existent, Batch not found",
            "batches/invalid-name, Invalid batch name format"
        })
        void should_throw_exception_when_batch_deletion_fails(String batchNameValue, String errorMessage) {
            // given
            var batchName = new BatchName(batchNameValue);
            when(mockGeminiService.batchDeleteBatch(batchName.value())).thenThrow(new RuntimeException(errorMessage));

            // when & then
            assertThatThrownBy(() -> subject.deleteBatchJob(batchName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);
        }
    }

    @Nested
    class ListBatchJobs {

        @Test
        void should_list_batch_jobs_with_default_parameters() {
            // given
            var operation1 = createMockOperation("batches/batch-1", BatchJobState.BATCH_STATE_SUCCEEDED);
            var operation2 = createMockOperation("batches/batch-2", BatchJobState.BATCH_STATE_RUNNING);
            var listResponse = new ListOperationsResponse<>(List.of(operation1, operation2), null);

            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(null, null))
                    .thenReturn(listResponse);

            // when
            BatchList<ChatResponse> result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.responses()).hasSize(2);
            verify(mockGeminiService).batchListBatches(null, null);
        }

        @Test
        void should_list_batch_jobs_with_page_size() {
            // given
            Integer pageSize = 10;
            var operation = createMockOperation("batches/batch-1", BatchJobState.BATCH_STATE_SUCCEEDED);
            var listResponse = new ListOperationsResponse<>(List.of(operation), "next-page-token");

            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(pageSize, null))
                    .thenReturn(listResponse);

            // when
            BatchList<ChatResponse> result = subject.listBatchJobs(pageSize, null);

            // then
            assertThat(result.responses()).hasSize(1);
            verify(mockGeminiService).batchListBatches(pageSize, null);
        }

        @Test
        void should_list_batch_jobs_with_page_token() {
            // given
            String pageToken = "token-123";
            var operation = createMockOperation("batches/batch-1", BatchJobState.BATCH_STATE_SUCCEEDED);
            var listResponse = new ListOperationsResponse<>(List.of(operation), null);

            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(null, pageToken))
                    .thenReturn(listResponse);

            // when
            BatchList<ChatResponse> result = subject.listBatchJobs(null, pageToken);

            // then
            assertThat(result.responses()).hasSize(1);
            verify(mockGeminiService).batchListBatches(null, pageToken);
        }

        @Test
        void should_list_batch_jobs_with_both_page_size_and_token() {
            // given
            Integer pageSize = 5;
            String pageToken = "token-456";
            var operation = createMockOperation("batches/batch-1", BatchJobState.BATCH_STATE_PENDING);
            var listResponse = new ListOperationsResponse<>(List.of(operation), "next-token");

            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(pageSize, pageToken))
                    .thenReturn(listResponse);

            // when
            BatchList<ChatResponse> result = subject.listBatchJobs(pageSize, pageToken);

            // then
            assertThat(result.responses()).hasSize(1);
            verify(mockGeminiService).batchListBatches(pageSize, pageToken);
        }

        @Test
        void should_return_empty_list_when_no_batch_jobs_exist() {
            // given
            var listResponse = new ListOperationsResponse<GeminiGenerateContentResponse>(List.of(), null);

            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(null, null))
                    .thenReturn(listResponse);

            // when
            BatchList<ChatResponse> result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.responses()).isEmpty();
            verify(mockGeminiService).batchListBatches(null, null);
        }

        @Test
        void should_handle_multiple_batch_jobs_with_different_states() {
            // given
            var operation1 = createMockOperation("batches/batch-1", BatchJobState.BATCH_STATE_SUCCEEDED);
            var operation2 = createMockOperation("batches/batch-2", BatchJobState.BATCH_STATE_FAILED);
            var operation3 = createMockOperation("batches/batch-3", BatchJobState.BATCH_STATE_CANCELLED);
            var listResponse = new ListOperationsResponse<>(List.of(operation1, operation2, operation3), null);

            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(null, null))
                    .thenReturn(listResponse);

            // when
            BatchList<ChatResponse> result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.responses()).hasSize(3);
            verify(mockGeminiService).batchListBatches(null, null);
        }

        @Test
        void should_throw_exception_when_listing_fails() {
            // given
            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(null, null))
                    .thenThrow(new RuntimeException("Server error"));

            // when & then
            assertThatThrownBy(() -> subject.listBatchJobs(null, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Server error");
        }

        @ParameterizedTest
        @CsvSource({"1, Invalid page size", "100, Invalid page size", "1000, Invalid page size"})
        void should_throw_exception_when_invalid_page_size(Integer pageSize, String errorMessage) {
            // given
            when(mockGeminiService.<List<GeminiGenerateContentResponse>>batchListBatches(pageSize, null))
                    .thenThrow(new RuntimeException(errorMessage));

            // when & then
            assertThatThrownBy(() -> subject.listBatchJobs(pageSize, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);
        }

        @Test
        void should_throw_exception_when_invalid_page_token() {
            // given
            String invalidToken = "invalid-token";
            when(mockGeminiService.<List<GeminiGenerateContentResponse>>batchListBatches(null, invalidToken))
                    .thenThrow(new RuntimeException("Invalid page token"));

            // when & then
            assertThatThrownBy(() -> subject.listBatchJobs(null, invalidToken))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid page token");
        }

        private Operation<GeminiGenerateContentResponse> createMockOperation(String name, BatchJobState state) {
            return new Operation<>(name, Map.of("state", state), false, null, null);
        }
    }

    @Nested
    class BatchChatSerialization {

        private static final String PENDING_RESPONSE =
                """
                {
                  "name": "batches/tti3ik8qob66dxcvynlg5swnutyntbi926ac",
                  "metadata": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatch",
                    "model": "models/gemini-2.5-flash-lite",
                    "displayName": "capitals-batch",
                    "createTime": "2025-12-03T17:23:06.004734302Z",
                    "updateTime": "2025-12-03T17:23:06.004734302Z",
                    "batchStats": {
                      "requestCount": "3",
                      "pendingRequestCount": "3"
                    },
                    "state": "BATCH_STATE_PENDING",
                    "name": "batches/tti3ik8qob66dxcvynlg5swnutyntbi926ac"
                  }
                }
                """;

        private static final String SUCCEEDED_RESPONSE =
                """
                {
                  "name": "batches/tti3ik8qob66dxcvynlg5swnutyntbi926ac",
                  "metadata": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatch",
                    "model": "models/gemini-2.5-flash-lite",
                    "displayName": "capitals-batch",
                    "createTime": "2025-12-03T17:23:06.004734302Z",
                    "endTime": "2025-12-03T17:24:41.850709659Z",
                    "updateTime": "2025-12-03T17:24:41.850709619Z",
                    "batchStats": {
                      "requestCount": "3",
                      "successfulRequestCount": "3"
                    },
                    "state": "BATCH_STATE_SUCCEEDED",
                    "name": "batches/tti3ik8qob66dxcvynlg5swnutyntbi926ac"
                  },
                  "done": true,
                  "response": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatchOutput",
                    "inlinedResponses": {
                      "inlinedResponses": [
                        {
                          "response": {
                            "candidates": [
                              {
                                "content": {
                                  "parts": [
                                    {
                                      "text": "The capital of France is **Paris**."
                                    }
                                  ],
                                  "role": "model"
                                },
                                "finishReason": "STOP",
                                "index": 0
                              }
                            ],
                            "usageMetadata": {
                              "promptTokenCount": 8,
                              "candidatesTokenCount": 8,
                              "totalTokenCount": 16
                            },
                            "modelVersion": "gemini-2.5-flash-lite"
                          }
                        },
                        {
                          "response": {
                            "candidates": [
                              {
                                "content": {
                                  "parts": [
                                    {
                                      "text": "The capital of Japan is **Tokyo**."
                                    }
                                  ],
                                  "role": "model"
                                },
                                "finishReason": "STOP",
                                "index": 0
                              }
                            ],
                            "usageMetadata": {
                              "promptTokenCount": 8,
                              "candidatesTokenCount": 8,
                              "totalTokenCount": 16
                            },
                            "modelVersion": "gemini-2.5-flash-lite"
                          }
                        },
                        {
                          "response": {
                            "candidates": [
                              {
                                "content": {
                                  "parts": [
                                    {
                                      "text": "The capital of Brazil is **Brasília**."
                                    }
                                  ],
                                  "role": "model"
                                },
                                "finishReason": "STOP",
                                "index": 0
                              }
                            ],
                            "usageMetadata": {
                              "promptTokenCount": 8,
                              "candidatesTokenCount": 9,
                              "totalTokenCount": 17
                            },
                            "modelVersion": "gemini-2.5-flash-lite"
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        @Test
        void should_deserialize_pending_batch_response() {
            // given
            var mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                    .body(PENDING_RESPONSE)
                    .statusCode(200)
                    .build());
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey("does not matter")
                    .modelName("does not matter")
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .build();

            var requests = List.of(
                    ChatRequest.builder()
                            .messages(UserMessage.from("What is the capital of France?"))
                            .build(),
                    ChatRequest.builder()
                            .messages(UserMessage.from("What is the capital of Japan?"))
                            .build(),
                    ChatRequest.builder()
                            .messages(UserMessage.from("What is the capital of Brazil?"))
                            .build());

            // when
            var result = subject.createBatchInline("capitals-batch", 0L, requests);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);
            var incomplete = (BatchIncomplete<?>) result;
            assertThat(incomplete.batchName().value()).isEqualTo("batches/tti3ik8qob66dxcvynlg5swnutyntbi926ac");
            assertThat(incomplete.state()).isEqualTo(BATCH_STATE_PENDING);
        }

        @Test
        void should_deserialize_succeeded_batch_response_with_chat_results() {
            // given
            var mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                    .body(SUCCEEDED_RESPONSE)
                    .statusCode(200)
                    .build());
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey("does not matter")
                    .modelName("does not matter")
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .build();
            var batchName = new BatchName("batches/tti3ik8qob66dxcvynlg5swnutyntbi926ac");

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var success = (BatchSuccess<ChatResponse>) result;
            assertThat(success.batchName().value()).isEqualTo("batches/tti3ik8qob66dxcvynlg5swnutyntbi926ac");

            var results = success.responses();
            assertThat(results).hasSize(3);

            assertThat(results.get(0).aiMessage().text()).isEqualTo("The capital of France is **Paris**.");
            assertThat(results.get(1).aiMessage().text()).isEqualTo("The capital of Japan is **Tokyo**.");
            assertThat(results.get(2).aiMessage().text()).isEqualTo("The capital of Brazil is **Brasília**.");
        }
    }

    private static Operation<GeminiGenerateContentResponse> createSuccessOperation(
            String operationName, List<ChatResponse> chatResponses) {
        var inlinedResponses = chatResponses.stream()
                .map(GoogleAiGeminiBatchChatModelTest::toGeminiResponse)
                .map(BatchCreateResponse.InlinedResponseWrapper::new)
                .toList();

        var response = new BatchCreateResponse<>(
                "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatchOutput",
                new BatchCreateResponse.InlinedResponses<>(inlinedResponses));

        return new Operation<>(operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, response);
    }

    private static Operation<GeminiGenerateContentResponse> createSuccessOperationWithNullResponse(
            String operationName) {
        return new Operation<>(operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, null);
    }

    private static Operation<GeminiGenerateContentResponse> createErrorOperation(
            String operationName, int errorCode, String errorMessage, List<Map<String, Object>> errorDetails) {
        var errorStatus = new Operation.Status(errorCode, errorMessage, errorDetails);
        return new Operation<>(operationName, Map.of("state", BATCH_STATE_FAILED.name()), true, errorStatus, null);
    }

    private static Operation<GeminiGenerateContentResponse> createCancelledOperation(
            String operationName, String errorMessage) {
        var errorStatus = new Operation.Status(13, errorMessage, List.of());
        return new Operation<>(operationName, Map.of("state", BATCH_STATE_CANCELLED.name()), true, errorStatus, null);
    }

    private static ChatResponse createChatResponse(String content) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(content))
                .metadata(ChatResponseMetadata.builder()
                        .id("response-id-" + content.hashCode())
                        .modelName(MODEL_NAME)
                        .tokenUsage(new TokenUsage(10, 5, 15))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();
    }

    private static GeminiGenerateContentResponse toGeminiResponse(ChatResponse chatResponse) {
        var part = GeminiPart.builder().text(chatResponse.aiMessage().text()).build();
        var content = new GeminiContent(List.of(part), "model");
        var candidate = new GeminiCandidate(content, GeminiFinishReason.STOP);
        var usageMetadata = GeminiUsageMetadata.builder()
                .promptTokenCount(chatResponse.metadata().tokenUsage().inputTokenCount())
                .candidatesTokenCount(chatResponse.metadata().tokenUsage().outputTokenCount())
                .totalTokenCount(chatResponse.metadata().tokenUsage().totalTokenCount())
                .build();

        return new GeminiGenerateContentResponse(
                chatResponse.id(), chatResponse.metadata().modelName(), List.of(candidate), usageMetadata);
    }

    private GoogleAiGeminiBatchChatModel createSubject() {
        return new GoogleAiGeminiBatchChatModel(
                GoogleAiGeminiBatchChatModel.builder().apiKey("does not matter").modelName(MODEL_NAME), mockGeminiService);
    }

    private static ChatRequest createChatRequest(String modelName, String message) {
        return ChatRequest.builder()
                .modelName(modelName)
                .messages(UserMessage.from(message))
                .build();
    }

    private static Operation<GeminiGenerateContentResponse> createPendingOperation(
            String operationName, BatchJobState state) {
        return new Operation<>(operationName, Map.of("state", state.name()), false, null, null);
    }
}
