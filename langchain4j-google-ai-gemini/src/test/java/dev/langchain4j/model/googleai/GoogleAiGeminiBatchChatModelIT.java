package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GoogleAiGeminiBatchChatModelIT {

    private static final String GOOGLE_AI_GEMINI_API_KEY = System.getenv("GOOGLE_AI_GEMINI_API_KEY");

    @Nested
    class CreateBatchInline {

        @Test
        void should_create_batch_with_valid_requests() {
            // given
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName("gemini-2.5-flash-lite")
                    .logRequests(true)
                    .logResponses(true)
                    .build();

            var displayName = "Test Batch - Valid Requests";
            var priority = 1L;
            var requests = List.of(
                    createChatRequest("gemini-2.5-flash-lite", "What is the capital of France?"),
                    createChatRequest("gemini-2.5-flash-lite", "What is the capital of Germany?")
            );

            // when
            var operation = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(operation.name()).startsWith("batches/");
            assertThat(operation.done()).isFalse();
            assertThat(operation.metadata()).isNotNull();
        }

        @Test
        void should_create_batch_with_negative_priority() {
            // given
            var subject = GoogleAiGeminiBatchChatModel.builder()
                    .apiKey(GOOGLE_AI_GEMINI_API_KEY)
                    .modelName("gemini-2.5-flash-lite")
                    .build();

            var displayName = "Test Batch - Negative Priority";
            var priority = -5L;
            var requests = List.of(
                    createChatRequest("gemini-2.5-flash-lite", "What is the capital of Portugal?")
            );

            // when
            var operation = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(operation).isNotNull();
            assertThat(operation.name()).isNotEmpty();
            assertThat(operation.done()).isFalse();
        }
    }

    private static ChatRequest createChatRequest(String modelName, String message) {
        return ChatRequest.builder()
                .modelName(modelName)
                .messages(UserMessage.from(message))
                .build();
    }
}
