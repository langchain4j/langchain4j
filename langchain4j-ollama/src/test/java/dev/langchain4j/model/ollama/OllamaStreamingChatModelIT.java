package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormat.JSON;
import static dev.langchain4j.model.ollama.OllamaImage.TINY_DOLPHIN_MODEL;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class OllamaStreamingChatModelIT extends AbstractOllamaLanguageModelInfrastructure {

    static final String MODEL_NAME = TINY_DOLPHIN_MODEL;

    @Test
    void should_respect_numPredict() {

        // given
        int numPredict = 1; // max output tokens

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .numPredict(numPredict)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(singletonList(userMessage), handler);
        ChatResponse response = handler.get();
        String answer = response.aiMessage().text();

        // then
        assertThat(answer).doesNotContain("Berlin");
        assertThat(response.aiMessage().text()).isEqualTo(answer);

        ChatResponseMetadata metadata = response.metadata();
        assertThat(metadata.modelName()).isEqualTo(MODEL_NAME);
        assertThat(metadata.finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(metadata.tokenUsage().outputTokenCount()).isBetween(numPredict, numPredict + 2); // bug in Ollama
    }

    @Test
    void should_generate_valid_json() {

        // given
        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .responseFormat(JSON)
                .temperature(0.0)
                .build();

        String userMessage = "Return JSON with two fields: name and age of John Doe, 42 years old.";

        // when
        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(userMessage, handler);
        dev.langchain4j.model.chat.response.ChatResponse response = handler.get();
        String answer = response.aiMessage().text();

        // then
        assertThat(answer).isEqualToIgnoringWhitespace("{\"name\": \"John Doe\", \"age\": 42}");
        assertThat(response.aiMessage().text()).isEqualTo(answer);
        ChatResponseMetadata metadata = response.metadata();
        assertThat(metadata.modelName()).isEqualTo(MODEL_NAME);
        assertThat(metadata.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_propagate_failure_to_handler_onError() throws Exception {

        // given
        String wrongModelName = "banana";

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(wrongModelName)
                .build();

        CompletableFuture<Throwable> future = new CompletableFuture<>();

        // when
        model.chat("does not matter", new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                future.completeExceptionally(new Exception("onPartialResponse() should never be called"));
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.completeExceptionally(new Exception("onCompleteResponse() should never be called"));
            }

            @Override
            public void onError(Throwable error) {
                future.complete(error);
            }
        });

        // then
        Throwable throwable = future.get();
        assertThat(throwable).isExactlyInstanceOf(ModelNotFoundException.class);
        assertThat(throwable.getMessage()).contains("banana", "not found");

        assertThat(throwable).hasCauseExactlyInstanceOf(HttpException.class);
        assertThat(((HttpException) throwable.getCause()).statusCode()).isEqualTo(404);
    }

    @Test
    void should_return_set_capabilities() {
        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .build();

        assertThat(model.supportedCapabilities()).contains(RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) throws Exception {

        // given
        Duration timeout = Duration.ofMillis(millis);

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .timeout(timeout)
                .build();

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        StreamingChatResponseHandler handler = new ErrorHandler(futureError);

        // when
        model.chat("hi", handler);

        // then
        Throwable error = futureError.get(5, SECONDS);

        assertThat(error).isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.ollama.OllamaChatModelIT#notSupportedContentTypesProvider")
    void should_throw_when_not_supported_content_types_used(List<ContentType> contentTypes) {

        // given

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .build();
        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        StreamingChatResponseHandler handler = new ErrorHandler(futureError);
        final UserMessage userMessage = OllamaChatModelIT.createUserMessageBasedOnContentTypes(contentTypes);

        // when-then

        assertThrows(dev.langchain4j.exception.UnsupportedFeatureException.class, () -> model.chat(List.of(userMessage), handler));
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.ollama.OllamaChatModelIT#supportedContentTypesProvider")
    void should_not_throw_when_supported_content_types_used(List<ContentType> contentTypes) throws Exception {

        // given

        StreamingChatModel model = OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl(ollama))
                .modelName(MODEL_NAME)
                .timeout(Duration.ofMillis(10))
                .build();
        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        StreamingChatResponseHandler handler = new ErrorHandler(futureError);
        final UserMessage userMessage = OllamaChatModelIT.createUserMessageBasedOnContentTypes(contentTypes);

        // when

        model.chat(List.of(userMessage), handler);

        // then
        // check that chat() times out, ergo, did not throw the UnsupportedFeatureException

        Throwable error = futureError.get(5, SECONDS);
        assertThat(error).isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    private record ErrorHandler(CompletableFuture<Throwable> futureError) implements StreamingChatResponseHandler {

        @Override
        public void onPartialResponse(String partialResponse) {
            futureError.completeExceptionally(new RuntimeException("onPartialResponse must not be called"));
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            futureError.completeExceptionally(new RuntimeException("onCompleteResponse must not be called"));
        }

        @Override
        public void onError(Throwable error) {
            futureError.complete(error);
        }
    }
}
