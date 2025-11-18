package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.GeminiBatchProcessor.BatchJobState.BATCH_STATE_CANCELLED;
import static dev.langchain4j.model.googleai.GeminiBatchProcessor.BatchJobState.BATCH_STATE_PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.googleai.GeminiBatchProcessor.BatchError;
import dev.langchain4j.model.googleai.GeminiBatchProcessor.BatchIncomplete;
import dev.langchain4j.model.googleai.GeminiBatchProcessor.BatchName;
import java.util.List;
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
                    .modelName("gemini-2.5-flash-lite")
                    .logRequestsAndResponses(true)
                    .build();

            var displayName = "Test Batch - Valid Requests";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("What is the capital of France?"),
                    createChatRequest("What is the capital of Germany?"));

            // when
            //            var response = subject.createBatchInline(displayName, priority, requests);
            //
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
                    .modelName("gemini-2.5-flash-lite")
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
                    .modelName("gemini-2.5-flash-lite")
                    .logRequestsAndResponses(true)
                    .build();

            // when & then
            var batchName = new BatchName("batches/test-batch");
            assertThatThrownBy(() -> subject.cancelBatchJob(batchName))
                    .isInstanceOf(HttpException.class)
                    .hasMessageContaining("\"message\": \"Could not parse the batch name\"");
        }
    }

    private static ChatRequest createChatRequest(String message) {
        return ChatRequest.builder()
                .modelName(MODEL_NAME)
                .messages(UserMessage.from(message))
                .build();
    }
}
