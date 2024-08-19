package dev.langchain4j.model.mistralai.internal.client;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.mistralai.internal.api.*;
import dev.langchain4j.spi.ServiceHelper;
import java.time.Duration;

public abstract class MistralAiClient {

    public abstract MistralAiChatCompletionResponse chatCompletion(MistralAiChatCompletionRequest request);

    public abstract void streamingChatCompletion(MistralAiChatCompletionRequest request, StreamingResponseHandler<AiMessage> handler);

    public abstract MistralAiEmbeddingResponse embedding(MistralAiEmbeddingRequest request);

    public abstract MistralAiModelResponse listModels();

    @SuppressWarnings("rawtypes")
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

        public abstract T build();

        public B baseUrl(String baseUrl) {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            return (B) this;
        }

        public B apiKey(String apiKey) {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("MistralAI API Key must be defined. It can be generated here: https://console.mistral.ai/user/api-keys");
            }
            this.apiKey = apiKey;
            return (B) this;
        }

        public B timeout(Duration timeout) {
            if (timeout == null) {
                throw new IllegalArgumentException("callTimeout cannot be null");
            }
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
    }
}
