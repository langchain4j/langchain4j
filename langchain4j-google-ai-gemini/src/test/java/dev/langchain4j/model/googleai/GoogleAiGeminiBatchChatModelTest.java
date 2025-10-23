package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchJobState.BATCH_STATE_CANCELLED;
import static dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchJobState.BATCH_STATE_FAILED;
import static dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchJobState.BATCH_STATE_PENDING;
import static dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchJobState.BATCH_STATE_RUNNING;
import static dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchJobState.BATCH_STATE_SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchGenerateContentResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchError;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchIncomplete;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchJobState;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchName;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchSuccess;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiBatchChatModelTest {
    @Mock
    private GeminiService mockGeminiService;

    @Captor
    private ArgumentCaptor<BatchRequestResponse.BatchGenerateContentRequest> batchRequestCaptor;

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
                    createChatRequest("gemini-2.5-flash-lite", "What is the capital of France?"),
                    createChatRequest("gemini-2.5-flash-lite", "What is the capital of Germany?"));
            var expectedOperation = createPendingOperation("batches/test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete(new BatchName("batches/test-123"), BATCH_STATE_PENDING));

            verify(mockGeminiService).batchGenerateContent(eq("gemini-2.5-flash-lite"), batchRequestCaptor.capture());

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isEqualTo(priority);
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(2);
        }

        @Test
        void should_create_batch_with_null_priority_defaulting_to_zero() {
            // given
            var displayName = "Test Batch";
            var requests = List.of(createChatRequest("gemini-2.5-flash-lite", "What is the capital of Italy?"));
            var expectedOperation = createPendingOperation("batches/test-456", BATCH_STATE_PENDING);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, null, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete(new BatchName("batches/test-456"), BATCH_STATE_PENDING));

            verify(mockGeminiService).batchGenerateContent(eq("gemini-2.5-flash-lite"), batchRequestCaptor.capture());

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isZero();
        }

        @Test
        void should_create_batch_with_single_request() {
            // given
            var displayName = "Single Request Batch";
            var priority = 5L;
            var requests = List.of(createChatRequest("gemini-2.5-pro", "Explain quantum computing"));
            var expectedOperation = createPendingOperation("batches/test-789", BATCH_STATE_PENDING);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete(new BatchName("batches/test-789"), BATCH_STATE_PENDING));

            verify(mockGeminiService).batchGenerateContent(eq("gemini-2.5-pro"), batchRequestCaptor.capture());

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
            var requests = List.of(createChatRequest("gemini-2.5-flash-lite", "What is AI?"));
            var expectedOperation = createPendingOperation("batches/test-negative", BATCH_STATE_PENDING);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete(new BatchName("batches/test-negative"), BATCH_STATE_PENDING));

            verify(mockGeminiService).batchGenerateContent(eq("gemini-2.5-flash-lite"), batchRequestCaptor.capture());

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().priority()).isEqualTo(-10L);
        }

        @Test
        void should_extract_correct_model_name_from_requests() {
            // given
            var displayName = "Model Extraction Test";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("gemini-2.5-pro", "Question 1"),
                    createChatRequest("gemini-2.5-pro", "Question 2"),
                    createChatRequest("gemini-2.5-pro", "Question 3"));
            var expectedOperation = createPendingOperation("batches/test-model", BATCH_STATE_PENDING);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);

            verify(mockGeminiService).batchGenerateContent(eq("gemini-2.5-pro"), any());
        }

        @Test
        void should_wrap_requests_in_inlined_request_with_empty_metadata() {
            // given
            var displayName = "Metadata Test";
            var priority = 1L;
            var requests = List.of(createChatRequest("gemini-2.5-flash-lite", "Test message"));
            var expectedOperation = createPendingOperation("batches/test-metadata", BATCH_STATE_PENDING);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);

            verify(mockGeminiService).batchGenerateContent(any(), batchRequestCaptor.capture());

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
                    createChatRequest("gemini-2.5-flash-lite", "Question 1"),
                    createChatRequest("gemini-2.5-flash-lite", "Question 2"),
                    createChatRequest("gemini-2.5-flash-lite", "Question 3"),
                    createChatRequest("gemini-2.5-flash-lite", "Question 4"),
                    createChatRequest("gemini-2.5-flash-lite", "Question 5"));
            var expectedOperation = createPendingOperation("batches/test-multiple", BATCH_STATE_PENDING);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete(new BatchName("batches/test-multiple"), BATCH_STATE_PENDING));

            verify(mockGeminiService).batchGenerateContent(eq("gemini-2.5-flash-lite"), batchRequestCaptor.capture());

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(5);
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
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(pendingOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete(batchName, BATCH_STATE_PENDING));
        }

        @Test
        void should_return_pending_when_batch_is_running() {
            // given
            var batchName = new BatchName("batches/test-running");
            var runningOperation = createPendingOperation("batches/test-running", BATCH_STATE_RUNNING);
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(runningOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete(batchName, BATCH_STATE_RUNNING));
        }

        @Test
        void should_return_success_when_batch_processing_is_completed() {
            // given
            var batchName = new BatchName("batches/test-success");
            var chatResponses = List.of(createChatResponse("Response 1"), createChatResponse("Response 2"));
            var successOperation = createSuccessOperation("batches/test-success", chatResponses);
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class).isEqualTo(new BatchSuccess(batchName, chatResponses));
        }

        @Test
        void should_return_success_with_empty_responses_when_response_is_null() {
            // given
            var batchName = new BatchName("batches/test-empty");
            var successOperation = createSuccessOperationWithNullResponse("batches/test-empty");
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class).isEqualTo(new BatchSuccess(batchName, List.of()));
        }

        @Test
        void should_return_error_when_batch_processing_is_cancelled() {
            // given
            var batchName = new BatchName("batches/test-error");
            var errorOperation =
                    createCancelledOperation("batches/test-error", "batches/test-error failed without error");
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(new BatchError(batchName,
                            13, "batches/test-error failed without error", BATCH_STATE_CANCELLED, List.of()));
        }

        @Test
        void should_return_error_when_batch_processing_fails() {
            // given
            var batchName = new BatchName("batches/test-error");
            var errorOperation = createErrorOperation("batches/test-error", 404, "Not Found", List.of());
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(new BatchError(batchName, 404, "Not Found", BatchJobState.BATCH_STATE_FAILED, List.of()));
        }

        @Test
        void should_return_error_with_details_when_batch_processing_fails_with_details() {
            // given
            var batchName = new BatchName("batches/test-error-details");
            List<Map<String, Object>> errorDetails = List.of(
                    Map.of("@type", "type.googleapis.com/google.rpc.ErrorInfo", "reason", "INVALID_ARGUMENT"),
                    Map.of("@type", "type.googleapis.com/google.rpc.BadRequest", "field", "model"));
            var errorOperation = createErrorOperation("batches/test-error-details", 400, "Bad Request", errorDetails);
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(new BatchError(batchName, 400, "Bad Request", BATCH_STATE_FAILED, errorDetails));
        }

        @Test
        void should_return_pending_with_unspecified_state_when_metadata_is_null() {
            // given
            var batchName = new BatchName("batches/test-no-metadata");
            var operation = new BatchRequestResponse.Operation("batches/test-no-metadata", null, false, null, null);
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(operation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete(batchName, BatchJobState.UNSPECIFIED));
        }

        @Test
        void should_return_pending_with_unspecified_state_when_state_is_missing_from_metadata() {
            // given
            var batchName = new BatchName("batches/test-no-state");
            var operation = new BatchRequestResponse.Operation(
                    "batches/test-no-state", Map.of("createTime", "2025-10-23T09:26:30Z"), false, null, null);
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(operation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete(batchName, BatchJobState.UNSPECIFIED));
        }

        @Test
        void should_return_success_with_single_response() {
            // given
            var batchName = new BatchName("batches/test-single");
            var chatResponses = List.of(createChatResponse("Single response"));
            var successOperation = createSuccessOperation("batches/test-single", chatResponses);
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class).isEqualTo(new BatchSuccess(batchName, chatResponses));
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
            when(mockGeminiService.batchRetrieveBatch(batchName.value())).thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class).isEqualTo(new BatchSuccess(batchName, chatResponses));
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

        @Test
        void should_delete_batch() {
            // given
            var batchName = new BatchName("batches/test-completed-delete");

            // when
            subject.deleteBatchJob(batchName);

            // then
            verify(mockGeminiService).batchDeleteBatch("batches/test-completed-delete");
        }
    }

    private static BatchRequestResponse.Operation createSuccessOperation(
            String operationName, List<ChatResponse> chatResponses) {
        var inlinedResponses = chatResponses.stream()
                .map(GoogleAiGeminiBatchChatModelTest::toGeminiResponse)
                .map(BatchGenerateContentResponse.InlinedResponseWrapper::new)
                .toList();

        var response = new BatchGenerateContentResponse.Response(
                "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatchOutput",
                new BatchGenerateContentResponse.InlinedResponses(inlinedResponses));

        return new BatchRequestResponse.Operation(
                operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, response);
    }

    private static BatchRequestResponse.Operation createSuccessOperationWithNullResponse(String operationName) {
        return new BatchRequestResponse.Operation(
                operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, null);
    }

    private static BatchRequestResponse.Operation createErrorOperation(
            String operationName, int errorCode, String errorMessage, List<Map<String, Object>> errorDetails) {
        var errorStatus = new BatchRequestResponse.Operation.Status(errorCode, errorMessage, errorDetails);
        return new BatchRequestResponse.Operation(
                operationName, Map.of("state", BATCH_STATE_FAILED.name()), true, errorStatus, null);
    }

    private static BatchRequestResponse.Operation createCancelledOperation(String operationName, String errorMessage) {
        var errorStatus = new BatchRequestResponse.Operation.Status(13, errorMessage, List.of());
        return new BatchRequestResponse.Operation(
                operationName, Map.of("state", BATCH_STATE_CANCELLED.name()), true, errorStatus, null);
    }

    private static ChatResponse createChatResponse(String content) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(content))
                .metadata(ChatResponseMetadata.builder()
                        .id("response-id-" + content.hashCode())
                        .modelName("gemini-2.5-flash-lite")
                        .tokenUsage(new TokenUsage(10, 5, 15))
                        .finishReason(FinishReason.STOP)
                        .build())
                .build();
    }

    private static GeminiGenerateContentResponse toGeminiResponse(ChatResponse chatResponse) {
        var part = GeminiPart.builder().text(chatResponse.aiMessage().text()).build();
        var content = new GeminiContent(List.of(part), "model");
        var candidate = GeminiCandidate.builder()
                .content(content)
                .finishReason(GeminiFinishReason.STOP)
                .build();
        var usageMetadata = GeminiUsageMetadata.builder()
                .promptTokenCount(chatResponse.metadata().tokenUsage().inputTokenCount())
                .candidatesTokenCount(chatResponse.metadata().tokenUsage().outputTokenCount())
                .totalTokenCount(chatResponse.metadata().tokenUsage().totalTokenCount())
                .build();

        return new GeminiGenerateContentResponse(
                chatResponse.id(),
                chatResponse.metadata().modelName(),
                List.of(candidate),
                GeminiPromptFeedback.builder().build(),
                usageMetadata);
    }

    private GoogleAiGeminiBatchChatModel createSubject() {
        return new GoogleAiGeminiBatchChatModel(
                GoogleAiGeminiBatchChatModel.builder().apiKey("apiKey"), mockGeminiService);
    }

    private static ChatRequest createChatRequest(String modelName, String message) {
        return ChatRequest.builder()
                .modelName(modelName)
                .messages(UserMessage.from(message))
                .build();
    }

    private static BatchRequestResponse.Operation createPendingOperation(String operationName, BatchJobState state) {
        return new BatchRequestResponse.Operation(operationName, Map.of("state", state.name()), false, null, null);
    }
}
