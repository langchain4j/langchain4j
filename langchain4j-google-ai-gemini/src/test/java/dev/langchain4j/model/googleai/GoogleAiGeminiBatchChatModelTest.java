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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchError;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchList;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchSuccess;
import dev.langchain4j.model.googleai.BatchRequestResponse.ListOperationsResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.Operation;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiUsageMetadata;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiBatchChatModelTest {
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
                    createChatRequest("gemini-2.5-flash-lite", "What is the capital of France?"),
                    createChatRequest("gemini-2.5-flash-lite", "What is the capital of Germany?"));
            var expectedOperation = createPendingOperation("batches/test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                    any(), ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-123"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq("gemini-2.5-flash-lite"), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

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
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                    any(), ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, null, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-456"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq("gemini-2.5-flash-lite"), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

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
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                    any(), ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-789"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq("gemini-2.5-pro"), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

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
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                    any(), ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-negative"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq("gemini-2.5-flash-lite"), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

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
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                    any(), ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);

            verify(mockGeminiService).batchCreate(eq("gemini-2.5-pro"), ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(), eq(BATCH_GENERATE_CONTENT));
        }

        @Test
        void should_wrap_requests_in_inlined_request_with_empty_metadata() {
            // given
            var displayName = "Metadata Test";
            var priority = 1L;
            var requests = List.of(createChatRequest("gemini-2.5-flash-lite", "Test message"));
            var expectedOperation = createPendingOperation("batches/test-metadata", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                    any(), ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(), eq(BATCH_GENERATE_CONTENT)))
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
                    createChatRequest("gemini-2.5-flash-lite", "Question 1"),
                    createChatRequest("gemini-2.5-flash-lite", "Question 2"),
                    createChatRequest("gemini-2.5-flash-lite", "Question 3"),
                    createChatRequest("gemini-2.5-flash-lite", "Question 4"),
                    createChatRequest("gemini-2.5-flash-lite", "Question 5"));
            var expectedOperation = createPendingOperation("batches/test-multiple", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                    any(), ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-multiple"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq("gemini-2.5-flash-lite"), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

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
                        .modelName("gemini-2.5-flash-lite")
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
                GoogleAiGeminiBatchChatModel.builder().apiKey("apiKey"), mockGeminiService);
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
