package dev.langchain4j.model.mistralai.internal.client;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.*;
import dev.langchain4j.spi.ServiceHelper;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;

@Internal
public abstract class MistralAiClient {

    public abstract MistralAiChatCompletionResponse chatCompletion(MistralAiChatCompletionRequest request);

    public ParsedAndRawResponse<MistralAiChatCompletionResponse> chatCompletionWithRawResponse(
            MistralAiChatCompletionRequest request) {
        MistralAiChatCompletionResponse parsedResponse = chatCompletion(request);
        return new ParsedAndRawResponse<>(parsedResponse, null);
    }

    public abstract void streamingChatCompletion(
            MistralAiChatCompletionRequest request, StreamingChatResponseHandler handler);

    public void streamingChatCompletion(
            MistralAiChatCompletionRequest request, StreamingChatResponseHandler handler, boolean returnThinking) {
        if (returnThinking) {
            throw new UnsupportedFeatureException("Returning thinking/reasoning content is not supported with this "
                    + "client implementation: " + getClass().getName());
        } else {
            streamingChatCompletion(request, handler);
        }
    }

    public abstract MistralAiEmbeddingResponse embedding(MistralAiEmbeddingRequest request);

    public ParsedAndRawResponse<MistralAiEmbeddingResponse> embeddingWithRawResponse(
            MistralAiEmbeddingRequest request) {
        MistralAiEmbeddingResponse parsedResponse = embedding(request);
        return new ParsedAndRawResponse<>(parsedResponse, null);
    }

    public abstract MistralAiModerationResponse moderation(MistralAiModerationRequest request);

    public abstract MistralAiModelResponse listModels();

    public abstract MistralAiChatCompletionResponse fimCompletion(MistralAiFimCompletionRequest request);

    public ParsedAndRawResponse<MistralAiChatCompletionResponse> fimCompletionWithRawResponse(
            MistralAiFimCompletionRequest request) {
        MistralAiChatCompletionResponse parsedResponse = fimCompletion(request);
        return new ParsedAndRawResponse<>(parsedResponse, null);
    }

    public abstract void streamingFimCompletion(
            MistralAiFimCompletionRequest request, StreamingResponseHandler<String> handler);

    @SuppressWarnings({"rawtypes"})
    public static MistralAiClient.Builder builder() {
        for (MistralAiClientBuilderFactory factory : ServiceHelper.loadFactories(MistralAiClientBuilderFactory.class)) {
            return factory.get();
        }
        // fallback to the default
        return DefaultMistralAiClient.builder();
    }

    @SuppressWarnings("unchecked")
    public abstract static class Builder<T extends MistralAiClient, B extends Builder<T, B>> {

        public String baseUrl;
        public String apiKey;
        public Duration timeout;
        public Boolean logRequests;
        public Boolean logResponses;
        public Logger logger;
        public HttpClientBuilder httpClientBuilder;
        public Supplier<Map<String, String>> customHeadersSupplier;

        public abstract T build();

        public B baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return (B) this;
        }

        public B apiKey(String apiKey) {
            this.apiKey = apiKey;
            return (B) this;
        }

        public B timeout(Duration timeout) {
            this.timeout = timeout;
            return (B) this;
        }

        public B logRequests() {
            return logRequests(true);
        }

        public B logRequests(Boolean logRequests) {
            if (logRequests == null) {
                logRequests = false;
            }
            this.logRequests = logRequests;
            return (B) this;
        }

        public B logResponses() {
            return logResponses(true);
        }

        public B logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }
            this.logResponses = logResponses;
            return (B) this;
        }

        public B logger(Logger logger) {
            this.logger = logger;
            return (B) this;
        }

        public B httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return (B) this;
        }

        public B customHeaders(java.util.Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return (B) this;
        }

        public B customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return (B) this;
        }
    }
}
