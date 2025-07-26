package dev.langchain4j.model.vertexai.anthropic;

import static dev.langchain4j.model.ModelProvider.*;
import static dev.langchain4j.model.vertexai.anthropic.VertexAiAnthropicFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junitpioneer.jupiter.RetryingTest;

/**
 * Integration tests for VertexAiAnthropicStreamingChatModel
 */
@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiAnthropicStreamingChatModelIT {

    private StreamingChatModel model;

    @BeforeEach
    void setUp() {
        model = VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .maxTokens(1000)
                .temperature(0.1)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (model instanceof AutoCloseable) {
            ((AutoCloseable) model).close();
        }
    }

    @RetryingTest(3)
    void should_stream_response() throws Exception {
        // given
        UserMessage userMessage = UserMessage.from(SIMPLE_QUESTION);
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(responseBuilder.toString());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        };

        // when
        model.chat(ChatRequest.builder().messages(List.of(userMessage)).build(), handler);

        // then
        String response = future.get(30, TimeUnit.SECONDS);
        assertThat(response).isNotBlank();
        assertThat(response).containsIgnoringCase(EXPECTED_ANSWER);
    }

    @RetryingTest(3)
    void should_handle_multiple_messages() throws Exception {
        // given
        UserMessage userMessage = UserMessage.from(COUNTING_QUESTION);
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(responseBuilder.toString());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        };

        // when
        model.chat(ChatRequest.builder().messages(List.of(userMessage)).build(), handler);

        // then
        String response = future.get(30, TimeUnit.SECONDS);
        assertThat(response).isNotBlank();
        assertThat(response).contains("1");
        assertThat(response).contains("5");
    }

    @Test
    void should_have_correct_provider() {
        assertThat(model.provider()).isEqualTo(GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @Test
    void should_build_with_all_parameters() {
        // given/when
        VertexAiAnthropicStreamingChatModel model = VertexAiAnthropicStreamingChatModel.builder()
                .project(DEFAULT_PROJECT)
                .location(DEFAULT_LOCATION)
                .modelName(DEFAULT_MODEL_NAME)
                .maxTokens(2048)
                .temperature(0.7)
                .topP(0.9)
                .topK(40)
                .stopSequences(List.of("STOP", "END"))
                .logRequests(true)
                .logResponses(false)
                .build();

        // then
        assertNotNull(model);
        assertThat(model.provider()).isEqualTo(GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @RetryingTest(3)
    void should_stream_with_stop_sequences() throws Exception {
        // given
        StreamingChatModel modelWithStopSequences = VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .maxTokens(1000)
                .stopSequences(List.of(STOP_WORD_1, STOP_WORD_2))
                .build();

        UserMessage userMessage = UserMessage.from("Count from 1 to 10, then say STOP");
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(responseBuilder.toString());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        };

        // when
        modelWithStopSequences.chat(
                ChatRequest.builder().messages(List.of(userMessage)).build(), handler);

        // then
        String response = future.get(30, TimeUnit.SECONDS);
        assertThat(response).isNotBlank();
        assertThat(response).doesNotContain("STOP");
    }

    @RetryingTest(3)
    void should_stream_with_special_characters() throws Exception {
        // given
        UserMessage userMessage = UserMessage.from("Process this text: " + SPECIAL_CHARACTERS);
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(responseBuilder.toString());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        };

        // when
        model.chat(ChatRequest.builder().messages(List.of(userMessage)).build(), handler);

        // then
        String response = future.get(30, TimeUnit.SECONDS);
        assertThat(response).isNotBlank();
    }

    @RetryingTest(3)
    void should_stream_with_unicode_text() throws Exception {
        // given
        UserMessage userMessage = UserMessage.from("Translate this: " + UNICODE_TEXT);
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(responseBuilder.toString());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        };

        // when
        model.chat(ChatRequest.builder().messages(List.of(userMessage)).build(), handler);

        // then
        String response = future.get(30, TimeUnit.SECONDS);
        assertThat(response).isNotBlank();
    }

    @Test
    void should_handle_streaming_minimal_input() throws Exception {
        // given
        UserMessage userMessage = UserMessage.from("Hi");
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(responseBuilder.toString());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        };

        // when
        model.chat(ChatRequest.builder().messages(List.of(userMessage)).build(), handler);

        // then - should handle minimal input gracefully
        String response = future.get(30, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response).isNotBlank();
    }

    @Test
    void should_validate_streaming_temperature_parameter() {
        // given/when
        VertexAiAnthropicStreamingChatModel modelWithTemp = VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .temperature(0.0)
                .build();

        // then
        assertNotNull(modelWithTemp);
        assertThat(modelWithTemp.provider()).isEqualTo(GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @Test
    void should_validate_streaming_top_p_parameter() {
        // given/when
        VertexAiAnthropicStreamingChatModel modelWithTopP = VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .topP(0.5)
                .build();

        // then
        assertNotNull(modelWithTopP);
        assertThat(modelWithTopP.provider()).isEqualTo(GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @Test
    void should_validate_streaming_top_k_parameter() {
        // given/when
        VertexAiAnthropicStreamingChatModel modelWithTopK = VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .topK(20)
                .build();

        // then
        assertNotNull(modelWithTopK);
        assertThat(modelWithTopK.provider()).isEqualTo(GOOGLE_VERTEX_AI_ANTHROPIC);
    }

    @Test
    void should_support_streaming_prompt_caching() throws Exception {
        // given
        VertexAiAnthropicStreamingChatModel modelWithCaching = VertexAiAnthropicStreamingChatModel.builder()
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .modelName(DEFAULT_MODEL_NAME)
                .enablePromptCaching(true)
                .maxTokens(1000)
                .build();

        // when - using a long system message to trigger caching
        SystemMessage longSystemMessage = SystemMessage.from(
                "You are a helpful assistant specialized in explaining complex topics. "
                        + "Your task is to provide clear, concise explanations that are easy to understand. "
                        + "Always provide examples when possible and break down complex concepts into simpler parts. "
                        + "This is a long system message that should be cached for efficiency when streaming responses.");
        UserMessage userMessage = UserMessage.from("What is machine learning?");

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        StringBuilder responseBuilder = new StringBuilder();

        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        };

        modelWithCaching.chat(
                ChatRequest.builder()
                        .messages(List.of(longSystemMessage, userMessage))
                        .build(),
                handler);

        // then
        ChatResponse response = future.get(30, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.metadata()).isNotNull();
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);

        TokenUsage tokenUsage = response.metadata().tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(0);

        // Verify partial responses were received
        String streamedResponse = responseBuilder.toString();
        assertThat(streamedResponse).isNotBlank();
    }

    @Test
    void should_build_streaming_with_caching_parameter() {
        // given/when
        VertexAiAnthropicStreamingChatModel streamingModelWithCaching = VertexAiAnthropicStreamingChatModel.builder()
                .project(DEFAULT_PROJECT)
                .location(DEFAULT_LOCATION)
                .modelName(DEFAULT_MODEL_NAME)
                .enablePromptCaching(true)
                .maxTokens(2048)
                .temperature(0.7)
                .build();

        // then
        assertNotNull(streamingModelWithCaching);
        assertThat(streamingModelWithCaching.provider()).isEqualTo(GOOGLE_VERTEX_AI_ANTHROPIC);
    }
}
