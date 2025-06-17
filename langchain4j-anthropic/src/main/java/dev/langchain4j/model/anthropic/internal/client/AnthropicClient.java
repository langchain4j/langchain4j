package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.Internal;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.spi.ServiceHelper;

import java.time.Duration;

@Internal
public abstract class AnthropicClient {

    public abstract AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request);

    public abstract void createMessage(AnthropicCreateMessageRequest request, StreamingChatResponseHandler handler);

    @SuppressWarnings("rawtypes")
    public static AnthropicClient.Builder builder() {
        for (AnthropicClientBuilderFactory factory : ServiceHelper.loadFactories(AnthropicClientBuilderFactory.class)) {
            return factory.get();
        }
        // fallback to the default
        return DefaultAnthropicClient.builder();
    }

    public abstract static class Builder<T extends AnthropicClient, B extends Builder<T, B>> {

        public HttpClientBuilder httpClientBuilder;
        public String baseUrl;
        public String apiKey;
        public String version;
        public String beta;
        public Duration timeout;
        public Boolean logRequests;
        public Boolean logResponses;

        public abstract T build();

        public B httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return (B) this;
        }

        public B baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return (B) this;
        }

        public B apiKey(String apiKey) {
            this.apiKey = apiKey;
            return (B) this;
        }

        public B version(String version) {
            this.version = version;
            return (B) this;
        }

        public B beta(String beta) {
            this.beta = beta;
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
    }
}
