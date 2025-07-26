package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCountTokensRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicRole;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;

import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.singletonList;

public class AnthropicTokenCountEstimator implements TokenCountEstimator {
    private final AnthropicClient client;
    private final String modelName;

    public AnthropicTokenCountEstimator(Builder builder) {
        this.client = AnthropicClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.anthropic.com/v1/"))
                .apiKey(builder.apiKey)
                .version(getOrDefault(builder.version, "2023-06-01"))
                .beta(builder.beta)
                .timeout(builder.timeout)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .build();

        this.modelName = ensureNotBlank(builder.modelName, "modelName");
    }

    @Override
    public int estimateTokenCountInText(final String text) {
        AnthropicCountTokensRequest request = AnthropicCountTokensRequest.builder()
                .model(this.modelName)
                .messages(singletonList(new AnthropicMessage(AnthropicRole.USER, singletonList(new AnthropicTextContent(text)))))
                .build();

        return client.countTokens(request).getInputTokens();
    }

    @Override
    public int estimateTokenCountInMessage(final ChatMessage message) {
        throw new UnsupportedOperationException("This method is not implemented yet.");
    }

    @Override
    public int estimateTokenCountInMessages(final Iterable<ChatMessage> messages) {
        throw new UnsupportedOperationException("This method is not implemented yet.");
    }

    private static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String version;
        private String beta;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private String modelName;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder beta(String beta) {
            this.beta = beta;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public AnthropicTokenCountEstimator build() {
            return new AnthropicTokenCountEstimator(this);
        }
    }
}
