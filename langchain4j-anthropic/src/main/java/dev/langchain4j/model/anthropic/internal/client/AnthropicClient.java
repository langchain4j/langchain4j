package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.ModelConstant;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.spi.ServiceHelper;

import java.time.Duration;
import java.util.Objects;

public abstract class AnthropicClient {

    static final String BASE_URL = "https://api.anthropic.com/v1/";
    static final String VERSION = "2023-06-01";

    public static final int DEFAULT_MAX_TOKENS = 1024;

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
        public boolean logRequests;
        public boolean logResponses;

        public Builder() {
            this.baseUrl = BASE_URL;
            this.version = VERSION;
            this.timeout = ModelConstant.DEFAULT_CLIENT_TIMEOUT;
            this.logRequests = false;
            this.logResponses = false;
        }

        public abstract T build();

        public B baseUrl(String baseUrl) {
            if (Objects.nonNull(baseUrl) && !baseUrl.trim().isEmpty()) {
                this.baseUrl = Utils.ensureTrailingForwardSlash(baseUrl);
            } // else { // keep default base url
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
            if (Objects.nonNull(version) && !version.trim().isEmpty()) {
                this.version = version;
            } // else { // keep default version
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
            if (Objects.nonNull(timeout)) {
                this.timeout = timeout;
            } // else { // keep default timeout
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
