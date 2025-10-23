package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchGenerateContentRequest;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiBatchChatModelTest {
    @Mock
    private GeminiService mockGeminiService;
    @Captor
    private ArgumentCaptor<BatchGenerateContentRequest> batchRequestCaptor;

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
                    createChatRequest("gemini-2.5-flash-lite", "What is the capital of Germany?")
            );

            var expectedOperation = new Operation("operations/test-123", null, false, null, null);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result).isEqualTo(expectedOperation);

            verify(mockGeminiService).batchGenerateContent(
                    eq("gemini-2.5-flash-lite"),
                    batchRequestCaptor.capture()
            );

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isEqualTo(priority);
            assertThat(capturedRequest.batch().inputConfig().requests().requests()).hasSize(2);
        }

        @Test
        void should_create_batch_with_null_priority_defaulting_to_zero() {
            // given
            var displayName = "Test Batch";
            var requests = List.of(
                    createChatRequest("gemini-2.5-flash-lite", "What is the capital of Italy?")
            );

            var expectedOperation = new Operation("operations/test-456", null, false, null, null);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, null, requests);

            // then
            assertThat(result).isEqualTo(expectedOperation);

            verify(mockGeminiService).batchGenerateContent(
                    eq("gemini-2.5-flash-lite"),
                    batchRequestCaptor.capture()
            );

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isZero();
        }

        @Test
        void should_create_batch_with_single_request() {
            // given
            var displayName = "Single Request Batch";
            var priority = 5L;
            var requests = List.of(
                    createChatRequest("gemini-2.5-pro", "Explain quantum computing")
            );

            var expectedOperation = new Operation("operations/test-789", null, false, null, null);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result).isEqualTo(expectedOperation);

            verify(mockGeminiService).batchGenerateContent(
                    eq("gemini-2.5-pro"),
                    batchRequestCaptor.capture()
            );

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().inputConfig().requests().requests()).hasSize(1);
        }

        @Test
        void should_throw_exception_when_requests_have_different_models() {
            // given
            var displayName = "Mixed Models Batch";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("gemini-2.5-flash", "Question 1"),
                    createChatRequest("gemini-2.5-pro", "Question 2")
            );

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
            var requests = List.of(
                    createChatRequest("gemini-2.5-flash-lite", "What is AI?")
            );

            var expectedOperation = new Operation("operations/test-negative", null, false, null, null);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result).isNotNull();

            verify(mockGeminiService).batchGenerateContent(
                    eq("gemini-2.5-flash-lite"),
                    batchRequestCaptor.capture()
            );

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
                    createChatRequest("gemini-2.5-pro", "Question 3")
            );

            var expectedOperation = new Operation("operations/test-model", null, false, null, null);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            subject.createBatchInline(displayName, priority, requests);

            // then
            verify(mockGeminiService).batchGenerateContent(
                    eq("gemini-2.5-pro"),
                    any()
            );
        }

        @Test
        void should_wrap_requests_in_inlined_request_with_empty_metadata() {
            // given
            var displayName = "Metadata Test";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("gemini-2.5-flash-lite", "Test message")
            );

            var expectedOperation = new Operation("operations/test-metadata", null, false, null, null);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            subject.createBatchInline(displayName, priority, requests);

            // then
            verify(mockGeminiService).batchGenerateContent(
                    any(),
                    batchRequestCaptor.capture()
            );

            var capturedRequest = batchRequestCaptor.getValue();
            var inlinedRequests = capturedRequest.batch().inputConfig().requests().requests();
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
                    createChatRequest("gemini-2.5-flash-lite", "Question 5")
            );

            var expectedOperation = new Operation("operations/test-multiple", null, false, null, null);
            when(mockGeminiService.batchGenerateContent(any(), any())).thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result).isNotNull();

            verify(mockGeminiService).batchGenerateContent(
                    eq("gemini-2.5-flash-lite"),
                    batchRequestCaptor.capture()
            );

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().inputConfig().requests().requests()).hasSize(5);
        }
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
}
