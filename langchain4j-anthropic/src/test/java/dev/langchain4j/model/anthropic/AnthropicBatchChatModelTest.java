package dev.langchain4j.model.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchIncomplete;
import dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchSuccess;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchIndividualResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchListResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchRequestCounts;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult.AnthropicBatchError;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult.AnthropicBatchResultCanceled;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult.AnthropicBatchResultError;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult.AnthropicBatchResultExpired;
import dev.langchain4j.model.anthropic.internal.api.AnthropicBatchResult.AnthropicBatchResultSuccess;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateBatchRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
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
class AnthropicBatchChatModelTest {

    private static final String MODEL_NAME = "claude-sonnet-4-5";

    @Mock
    private AnthropicClient mockClient;

    @Captor
    private ArgumentCaptor<AnthropicCreateBatchRequest> batchRequestCaptor;

    private AnthropicBatchChatModel subject;

    @BeforeEach
    void setUp() {
        subject = createSubject();
    }

    @Nested
    class CreateBatchInline {

        @Test
        void should_create_batch_with_valid_requests() {
            // given
            var requests = List.of(
                    createChatRequest("What is the capital of France?"),
                    createChatRequest("What is the capital of Finland?"),
                    createChatRequest("What is the capital of Germany?"));
            var expectedResponse = createInProgressResponse("msgbatch_test123");
            when(mockClient.createBatch(any(AnthropicCreateBatchRequest.class))).thenReturn(expectedResponse);

            // when
            var result = subject.createBatchInline(requests);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<?>) result;
            assertThat(incomplete.name().id()).isEqualTo("msgbatch_test123");
            assertThat(incomplete.processingStatus()).isEqualTo("in_progress");

            verify(mockClient).createBatch(batchRequestCaptor.capture());
            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.requests()).hasSize(3);
        }

        @Test
        void should_create_batch_with_single_request() {
            // given
            var requests = List.of(createChatRequest("Explain quantum computing"));
            var expectedResponse = createInProgressResponse("msgbatch_single");
            when(mockClient.createBatch(any(AnthropicCreateBatchRequest.class))).thenReturn(expectedResponse);

            // when
            var result = subject.createBatchInline(requests);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<?>) result;
            assertThat(incomplete.name().id()).isEqualTo("msgbatch_single");

            verify(mockClient).createBatch(batchRequestCaptor.capture());
            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.requests()).hasSize(1);
        }

        @Test
        void should_create_batch_with_custom_id() {
            // given
            var customId = "my-custom-id";
            var requests = List.of(createChatRequest("Test message"));
            var expectedResponse = createInProgressResponse("msgbatch_custom");
            when(mockClient.createBatch(any(AnthropicCreateBatchRequest.class))).thenReturn(expectedResponse);

            // when
            var result = subject.createBatchInline(customId, requests);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);

            verify(mockClient).createBatch(batchRequestCaptor.capture());
            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.requests()).hasSize(1);
            assertThat(capturedRequest.requests().get(0).customId()).isEqualTo(customId);
        }

        @Test
        void should_create_batch_with_multiple_requests() {
            // given
            var requests = List.of(
                    createChatRequest("Question 1"),
                    createChatRequest("Question 2"),
                    createChatRequest("Question 3"),
                    createChatRequest("Question 4"),
                    createChatRequest("Question 5"));
            var expectedResponse = createInProgressResponse("msgbatch_multiple");
            when(mockClient.createBatch(any(AnthropicCreateBatchRequest.class))).thenReturn(expectedResponse);

            // when
            var result = subject.createBatchInline(requests);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<?>) result;
            assertThat(incomplete.name().id()).isEqualTo("msgbatch_multiple");

            verify(mockClient).createBatch(batchRequestCaptor.capture());
            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.requests()).hasSize(5);
        }

        @Test
        void should_throw_exception_when_service_fails() {
            // given
            var requests = List.of(createChatRequest("Test"));
            when(mockClient.createBatch(any(AnthropicCreateBatchRequest.class)))
                    .thenThrow(new RuntimeException("Service unavailable"));

            // when & then
            assertThatThrownBy(() -> subject.createBatchInline(requests))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Service unavailable");
        }
    }

    @Nested
    class RetrieveBatchResults {

        @Test
        void should_throw_when_invalid_batch_name() {
            assertThatThrownBy(() -> dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of(
                            "invalid-name"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("msgbatch_");
        }

        @Test
        void should_return_incomplete_when_batch_is_in_progress() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_pending");
            var response = createInProgressResponse("msgbatch_pending");
            when(mockClient.retrieveBatch("msgbatch_pending")).thenReturn(response);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<?>) result;
            assertThat(incomplete.name()).isEqualTo(batchName);
            assertThat(incomplete.processingStatus()).isEqualTo("in_progress");
        }

        @Test
        void should_return_incomplete_when_batch_is_canceling() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_canceling");
            var response = createCancelingResponse("msgbatch_canceling");
            when(mockClient.retrieveBatch("msgbatch_canceling")).thenReturn(response);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<?>) result;
            assertThat(incomplete.name()).isEqualTo(batchName);
            assertThat(incomplete.processingStatus()).isEqualTo("canceling");
        }

        @Test
        void should_return_success_when_batch_is_ended_with_results() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_success");
            var response = createEndedResponse("msgbatch_success", "https://api.anthropic.com/results");
            var individualResults = List.of(
                    createSuccessfulIndividualResponse("req-1", "Response 1"),
                    createSuccessfulIndividualResponse("req-2", "Response 2"));
            when(mockClient.retrieveBatch("msgbatch_success")).thenReturn(response);
            when(mockClient.retrieveBatchResults("msgbatch_success")).thenReturn(individualResults);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchSuccess.class);
            var success = (AnthropicBatchSuccess<ChatResponse>) result;
            assertThat(success.name()).isEqualTo(batchName);
            assertThat(success.results()).hasSize(2);
            assertThat(success.results().get(0).customId()).isEqualTo("req-1");
            assertThat(success.results().get(0).result().aiMessage().text()).isEqualTo("Response 1");
            assertThat(success.results().get(1).customId()).isEqualTo("req-2");
            assertThat(success.results().get(1).result().aiMessage().text()).isEqualTo("Response 2");
        }

        @Test
        void should_return_success_with_mixed_results() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_mixed");
            var response =
                    createEndedResponseWithCounts("msgbatch_mixed", "https://api.anthropic.com/results", 1, 1, 1, 1);
            var individualResults = List.of(
                    createSuccessfulIndividualResponse("req-1", "Success"),
                    createErroredIndividualResponse("req-2", "invalid_request", "Invalid model"),
                    createCanceledIndividualResponse("req-3"),
                    createExpiredIndividualResponse("req-4"));
            when(mockClient.retrieveBatch("msgbatch_mixed")).thenReturn(response);
            when(mockClient.retrieveBatchResults("msgbatch_mixed")).thenReturn(individualResults);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchSuccess.class);
            var success = (AnthropicBatchSuccess<ChatResponse>) result;
            assertThat(success.results()).hasSize(4);

            assertThat(success.results().get(0).isSucceeded()).isTrue();
            assertThat(success.results().get(0).result().aiMessage().text()).isEqualTo("Success");

            assertThat(success.results().get(1).isErrored()).isTrue();
            assertThat(success.results().get(1).error()).contains("invalid_request");

            assertThat(success.results().get(2).isCanceled()).isTrue();
            assertThat(success.results().get(2).error()).contains("canceled");

            assertThat(success.results().get(3).isExpired()).isTrue();
            assertThat(success.results().get(3).error()).contains("expired");
        }

        @Test
        void should_return_error_when_all_requests_failed() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_allfailed");
            var response = createEndedResponseWithCounts("msgbatch_allfailed", null, 0, 3, 0, 0);
            when(mockClient.retrieveBatch("msgbatch_allfailed")).thenReturn(response);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            if (result instanceof dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchError<?> error) {
                assertThat(error.name()).isEqualTo(batchName);
                assertThat(error.errorMessage()).contains("All requests in batch failed");
            } else {
                fail("result is not an AnthropicBatchError");
            }
        }

        @Test
        void should_return_incomplete_when_ended_but_no_results_url() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_nourl");
            var response = createEndedResponseWithCounts("msgbatch_nourl", null, 2, 0, 0, 0);
            when(mockClient.retrieveBatch("msgbatch_nourl")).thenReturn(response);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
        }

        @Test
        void should_return_success_with_single_response() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_single");
            var response = createEndedResponse("msgbatch_single", "https://api.anthropic.com/results");
            var individualResults = List.of(createSuccessfulIndividualResponse("req-1", "Single response"));
            when(mockClient.retrieveBatch("msgbatch_single")).thenReturn(response);
            when(mockClient.retrieveBatchResults("msgbatch_single")).thenReturn(individualResults);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchSuccess.class);
            var success = (AnthropicBatchSuccess<ChatResponse>) result;
            assertThat(success.results()).hasSize(1);
            assertThat(success.results().get(0).result().aiMessage().text()).isEqualTo("Single response");
        }

        @Test
        void should_handle_null_request_counts() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_nullcounts");
            var response = new AnthropicBatchResponse(
                    "msgbatch_nullcounts",
                    "message_batch",
                    "in_progress",
                    null, // null request counts
                    null,
                    null,
                    null,
                    null,
                    null);
            when(mockClient.retrieveBatch("msgbatch_nullcounts")).thenReturn(response);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(AnthropicBatchIncomplete.class);
            var incomplete = (AnthropicBatchIncomplete<?>) result;
            assertThat(incomplete.processingCount()).isZero();
            assertThat(incomplete.succeededCount()).isZero();
            assertThat(incomplete.erroredCount()).isZero();
        }
    }

    @Nested
    class CancelBatchJob {

        @Test
        void should_cancel_batch() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_tocancel");
            var response = createCancelingResponse("msgbatch_tocancel");
            when(mockClient.cancelBatch("msgbatch_tocancel")).thenReturn(response);

            // when
            subject.cancelBatchJob(batchName);

            // then
            verify(mockClient).cancelBatch("msgbatch_tocancel");
        }

        @ParameterizedTest
        @CsvSource({
            "msgbatch_completed, Batch cannot be cancelled because it has already completed",
            "msgbatch_cancelled, Batch is already cancelled",
            "msgbatch_notfound, Batch not found"
        })
        void should_throw_exception_when_batch_cancellation_fails(String batchId, String errorMessage) {
            // given
            var batchName = dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of(batchId);
            when(mockClient.cancelBatch(batchId)).thenThrow(new RuntimeException(errorMessage));

            // when & then
            assertThatThrownBy(() -> subject.cancelBatchJob(batchName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);
        }
    }

    @Nested
    class ListBatchJobs {

        @Test
        void should_list_batch_jobs_with_default_parameters() {
            // given
            var batch1 = createInProgressResponse("msgbatch_1");
            var batch2 = createInProgressResponse("msgbatch_2");
            var listResponse =
                    new AnthropicBatchListResponse(List.of(batch1, batch2), false, "msgbatch_1", "msgbatch_2");
            when(mockClient.listBatches(null, null)).thenReturn(listResponse);

            // when
            var result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.batches()).hasSize(2);
            assertThat(result.hasMore()).isFalse();
            verify(mockClient).listBatches(null, null);
        }

        @Test
        void should_list_batch_jobs_with_page_size() {
            // given
            Integer pageSize = 10;
            var batch = createInProgressResponse("msgbatch_1");
            var listResponse = new AnthropicBatchListResponse(List.of(batch), true, "msgbatch_1", "msgbatch_1");
            when(mockClient.listBatches(pageSize, null)).thenReturn(listResponse);

            // when
            var result = subject.listBatchJobs(pageSize, null);

            // then
            assertThat(result.batches()).hasSize(1);
            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextPageToken()).isEqualTo("msgbatch_1");
            verify(mockClient).listBatches(pageSize, null);
        }

        @Test
        void should_list_batch_jobs_with_page_token() {
            // given
            String pageToken = "msgbatch_prev";
            var batch = createInProgressResponse("msgbatch_next");
            var listResponse = new AnthropicBatchListResponse(List.of(batch), false, "msgbatch_next", "msgbatch_next");
            when(mockClient.listBatches(null, pageToken)).thenReturn(listResponse);

            // when
            var result = subject.listBatchJobs(null, pageToken);

            // then
            assertThat(result.batches()).hasSize(1);
            verify(mockClient).listBatches(null, pageToken);
        }

        @Test
        void should_list_batch_jobs_with_both_page_size_and_token() {
            // given
            Integer pageSize = 5;
            String pageToken = "msgbatch_token";
            var batch = createInProgressResponse("msgbatch_result");
            var listResponse =
                    new AnthropicBatchListResponse(List.of(batch), true, "msgbatch_result", "msgbatch_result");
            when(mockClient.listBatches(pageSize, pageToken)).thenReturn(listResponse);

            // when
            var result = subject.listBatchJobs(pageSize, pageToken);

            // then
            assertThat(result.batches()).hasSize(1);
            verify(mockClient).listBatches(pageSize, pageToken);
        }

        @Test
        void should_return_empty_list_when_no_batch_jobs_exist() {
            // given
            var listResponse = new AnthropicBatchListResponse(List.of(), false, null, null);
            when(mockClient.listBatches(null, null)).thenReturn(listResponse);

            // when
            var result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.batches()).isEmpty();
            assertThat(result.hasMore()).isFalse();
        }

        @Test
        void should_handle_multiple_batch_jobs_with_different_states() {
            // given
            var batch1 = createInProgressResponse("msgbatch_1");
            var batch2 = createCancelingResponse("msgbatch_2");
            var batch3 = createEndedResponse("msgbatch_3", "https://results");
            var listResponse =
                    new AnthropicBatchListResponse(List.of(batch1, batch2, batch3), false, "msgbatch_1", "msgbatch_3");
            when(mockClient.listBatches(null, null)).thenReturn(listResponse);

            // when
            var result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.batches()).hasSize(3);
        }

        @Test
        void should_throw_exception_when_listing_fails() {
            // given
            when(mockClient.listBatches(null, null)).thenThrow(new RuntimeException("Server error"));

            // when & then
            assertThatThrownBy(() -> subject.listBatchJobs(null, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Server error");
        }
    }

    @Nested
    class AnthropicBatchNameValidation {

        @Test
        void should_create_valid_batch_name() {
            // given & when
            var batchName = dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of(
                    "msgbatch_01HkcTjaV5uDC8jWR4ZsDV8d");

            // then
            assertThat(batchName.id()).isEqualTo("msgbatch_01HkcTjaV5uDC8jWR4ZsDV8d");
        }

        @Test
        void should_throw_for_null_id() {
            assertThatThrownBy(() -> dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void should_throw_for_blank_id() {
            assertThatThrownBy(
                            () -> dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void should_throw_for_invalid_prefix() {
            assertThatThrownBy(() ->
                            dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("batch_123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("msgbatch_");
        }

        @Test
        void should_return_id_from_toString() {
            // given
            var batchName =
                    dev.langchain4j.model.anthropic.AnthropicBatchResponse.AnthropicBatchName.of("msgbatch_test");

            // then
            assertThat(batchName.toString()).isEqualTo("msgbatch_test");
        }
    }

    // Helper methods

    private AnthropicBatchChatModel createSubject() {
        return new AnthropicBatchChatModel(
                AnthropicBatchChatModel.builder().apiKey("test-api-key").modelName(MODEL_NAME), mockClient);
    }

    private static ChatRequest createChatRequest(String message) {
        return ChatRequest.builder().messages(UserMessage.from(message)).build();
    }

    private static AnthropicBatchResponse createInProgressResponse(String batchId) {
        return new AnthropicBatchResponse(
                batchId,
                "message_batch",
                "in_progress",
                new AnthropicBatchRequestCounts(2, 0, 0, 0, 0),
                null,
                "2024-09-24T18:37:24.100435Z",
                "2024-09-25T18:37:24.100435Z",
                null,
                null);
    }

    private static AnthropicBatchResponse createCancelingResponse(String batchId) {
        return new AnthropicBatchResponse(
                batchId,
                "message_batch",
                "canceling",
                new AnthropicBatchRequestCounts(1, 0, 0, 0, 0),
                null,
                "2024-09-24T18:37:24.100435Z",
                "2024-09-25T18:37:24.100435Z",
                "2024-09-24T18:39:03.114875Z",
                null);
    }

    private static AnthropicBatchResponse createEndedResponse(String batchId, String resultsUrl) {
        return new AnthropicBatchResponse(
                batchId,
                "message_batch",
                "ended",
                new AnthropicBatchRequestCounts(0, 2, 0, 0, 0),
                "2024-09-24T19:37:24.100435Z",
                "2024-09-24T18:37:24.100435Z",
                "2024-09-25T18:37:24.100435Z",
                null,
                resultsUrl);
    }

    private static AnthropicBatchResponse createEndedResponseWithCounts(
            String batchId, String resultsUrl, int succeeded, int errored, int canceled, int expired) {
        return new AnthropicBatchResponse(
                batchId,
                "message_batch",
                "ended",
                new AnthropicBatchRequestCounts(0, succeeded, errored, canceled, expired),
                "2024-09-24T19:37:24.100435Z",
                "2024-09-24T18:37:24.100435Z",
                "2024-09-25T18:37:24.100435Z",
                null,
                resultsUrl);
    }

    private static AnthropicBatchIndividualResponse createSuccessfulIndividualResponse(String customId, String text) {
        var content = AnthropicContent.builder().type("text").text(text).build();
        var messageResponse = new AnthropicCreateMessageResponse(
                "msg_" + customId,
                "message",
                "assistant",
                List.of(content),
                MODEL_NAME,
                "end_turn",
                null,
                new AnthropicUsage(10, 20, null, null));
        return new AnthropicBatchIndividualResponse(customId, new AnthropicBatchResultSuccess(messageResponse));
    }

    private static AnthropicBatchIndividualResponse createErroredIndividualResponse(
            String customId, String errorType, String errorMessage) {
        return new AnthropicBatchIndividualResponse(
                customId, new AnthropicBatchResultError(new AnthropicBatchError(errorType, errorMessage)));
    }

    private static AnthropicBatchIndividualResponse createCanceledIndividualResponse(String customId) {
        return new AnthropicBatchIndividualResponse(customId, new AnthropicBatchResultCanceled());
    }

    private static AnthropicBatchIndividualResponse createExpiredIndividualResponse(String customId) {
        return new AnthropicBatchIndividualResponse(customId, new AnthropicBatchResultExpired());
    }
}
