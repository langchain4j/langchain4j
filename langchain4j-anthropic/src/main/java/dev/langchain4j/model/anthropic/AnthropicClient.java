package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.spi.ServiceHelper;

import java.time.Duration;

public abstract class AnthropicClient {

    public abstract AnthropicCreateMessageResponse createMessage(AnthropicCreateMessageRequest request);

    public abstract void createMessage(AnthropicCreateMessageRequest request, StreamingResponseHandler<AiMessage> handler);

    @SuppressWarnings("rawtypes")
    public static AnthropicClient.Builder builder() {
        for (AnthropicClientBuilderFactory factory : ServiceHelper.loadFactories(AnthropicClientBuilderFactory.class)) {
            return factory.get();
        }
        // fallback to the default
        return DefaultAnthropicClient.builder();
    }

    public abstract static class Builder<T extends AnthropicClient, B extends Builder<T, B>> {

        public String baseUrl;
        public String apiKey;
        public String version;
        public String beta;
        public Duration timeout;
        public Boolean logRequests;
        public Boolean logResponses;

        public abstract T build();

        public B baseUrl(String baseUrl) {
            if ((baseUrl == null) || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
            this.baseUrl = baseUrl;
            return (B) this;
        }

        public B apiKey(String apiKey) {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("Anthropic API key must be defined. " +
                        "It can be generated here: https://console.anthropic.com/settings/keys");
            }
            this.apiKey = apiKey;
            return (B) this;
        }

        public B version(String version) {
            if (version == null) {
                throw new IllegalArgumentException("version cannot be null or empty");
            }
            this.version = version;
            return (B) this;
        }

        public B beta(String beta) {
            if (beta == null) {
                throw new IllegalArgumentException("beta cannot be null or empty");
            }
            this.beta = beta;
            return (B) this;
        }

        public B timeout(Duration timeout) {
            if (timeout == null) {
                throw new IllegalArgumentException("timeout cannot be null");
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
