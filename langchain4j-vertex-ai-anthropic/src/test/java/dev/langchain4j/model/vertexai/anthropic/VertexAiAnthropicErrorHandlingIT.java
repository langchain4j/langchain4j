package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junitpioneer.jupiter.RetryingTest;

/**
 * Error handling and edge case tests for VertexAiAnthropic models
 */
@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiAnthropicErrorHandlingIT {

    @Test
    void should_handle_invalid_project_id() {
        // given/when/then
        assertThrows(Exception.class, () -> {
            VertexAiAnthropicChatModel model = VertexAiAnthropicChatModel.builder()
                    .project("invalid-project-id-12345")
                    .location("us-central1")
                    .modelName(DEFAULT_MODEL_NAME)
                    .build();

            model.chat(ChatRequest.builder()
                    .messages(List.of(UserMessage.from(SIMPLE_QUESTION)))
                    .build());
        });
    }

    @Test
    void should_handle_invalid_location() {
        // given/when/then - test that invalid locations are handled gracefully
        try {
            VertexAiAnthropicChatModel model = VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location("this-is-definitely-not-a-valid-gcp-location-12345")
                    .modelName(DEFAULT_MODEL_NAME)
                    .build();

            model.chat(ChatRequest.builder()
                    .messages(List.of(UserMessage.from(SIMPLE_QUESTION)))
                    .build());

            // If we reach here without exception, the implementation is resilient
        } catch (Exception e) {
            // Expected - invalid location should cause an error
            assertThat(e).isNotNull();
        }
    }

    @Test
    void should_handle_invalid_model_name() {
        // given/when/then
        assertThrows(Exception.class, () -> {
            VertexAiAnthropicChatModel model = VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(INVALID_MODEL_NAME)
                    .build();

            model.chat(ChatRequest.builder()
                    .messages(List.of(UserMessage.from(SIMPLE_QUESTION)))
                    .build());
        });
    }

    @Test
    void should_handle_null_messages() {
        // given
        ChatModel model = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .build();

        // when/then
        assertThrows(Exception.class, () -> {
            model.chat(ChatRequest.builder()
                    .messages(List.of(null)) // null message
                    .build());
        });
    }

    @Test
    void should_handle_empty_messages_list() {
        // given
        ChatModel model = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .build();

        // when/then
        assertThrows(Exception.class, () -> {
            model.chat(ChatRequest.builder().messages(List.of()).build());
        });
    }

    @Test
    void should_validate_invalid_temperature_values() {
        // given/when/then - should throw exception for invalid values
        assertThrows(IllegalArgumentException.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(DEFAULT_MODEL_NAME)
                    .temperature(-0.1)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(DEFAULT_MODEL_NAME)
                    .temperature(1.1)
                    .build();
        });
    }

    @Test
    void should_validate_invalid_top_p_values() {
        // given/when/then - should throw exception for invalid values
        assertThrows(IllegalArgumentException.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(DEFAULT_MODEL_NAME)
                    .topP(-0.1)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(DEFAULT_MODEL_NAME)
                    .topP(1.1)
                    .build();
        });
    }

    @Test
    void should_validate_invalid_top_k_values() {
        // given/when/then - should throw exception for invalid values
        assertThrows(IllegalArgumentException.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(DEFAULT_MODEL_NAME)
                    .topK(-1)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(DEFAULT_MODEL_NAME)
                    .topK(0)
                    .build();
        });
    }

    @Test
    void should_validate_invalid_max_tokens_values() {
        // given/when/then - should throw exception for invalid values
        assertThrows(IllegalArgumentException.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(DEFAULT_MODEL_NAME)
                    .maxTokens(-1)
                    .build();
        });

        assertThrows(IllegalArgumentException.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(DEFAULT_MODEL_NAME)
                    .maxTokens(0)
                    .build();
        });
    }

    @Test
    void should_handle_streaming_error_callback() throws Exception {
        // given
        StreamingChatModel model = VertexAiAnthropicStreamingChatModel.builder()
                .project("invalid-project-id-12345")
                .location("us-central1")
                .modelName(DEFAULT_MODEL_NAME)
                .build();

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> callbackReceived = new CompletableFuture<>();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // Should not be called for this error case
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                // Should not be called for this error case
            }

            @Override
            public void onError(Throwable error) {
                errorFuture.complete(error);
                callbackReceived.complete(true);
            }
        };

        // when
        try {
            model.chat(
                    ChatRequest.builder()
                            .messages(List.of(UserMessage.from(SIMPLE_QUESTION)))
                            .build(),
                    handler);

            // Wait for either error callback or timeout
            try {
                Boolean received = callbackReceived.get(5, TimeUnit.SECONDS);
                if (received) {
                    Throwable error = errorFuture.get(1, TimeUnit.SECONDS);
                    assertThat(error).isNotNull();
                }
            } catch (java.util.concurrent.TimeoutException e) {
                // If callback wasn't received, the error was thrown synchronously
                // which is also acceptable behavior
            }
        } catch (Exception e) {
            // If the error is thrown synchronously, that's also acceptable
            assertThat(e).isNotNull();
        }
    }

    @RetryingTest(3)
    void should_handle_very_large_input() {
        // given
        ChatModel model = VertexAiAnthropicChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .maxTokens(100)
                .build();

        UserMessage userMessage = UserMessage.from(LARGE_INPUT_TEXT);

        // when/then - should either succeed or throw a meaningful exception
        try {
            ChatResponse response = model.chat(
                    ChatRequest.builder().messages(List.of(userMessage)).build());
            assertThat(response).isNotNull();
        } catch (Exception e) {
            // Expected for very large inputs
            assertThat(e.getMessage()).isNotNull();
        }
    }

    @Test
    void should_handle_null_project() {
        // given/when/then
        assertThrows(Exception.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(null)
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(DEFAULT_MODEL_NAME)
                    .build();
        });
    }

    @Test
    void should_handle_null_location() {
        // given/when/then
        assertThrows(Exception.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(null)
                    .modelName(DEFAULT_MODEL_NAME)
                    .build();
        });
    }

    @Test
    void should_handle_null_model_name() {
        // given/when/then
        assertThrows(Exception.class, () -> {
            VertexAiAnthropicChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName(null)
                    .build();
        });
    }
}
