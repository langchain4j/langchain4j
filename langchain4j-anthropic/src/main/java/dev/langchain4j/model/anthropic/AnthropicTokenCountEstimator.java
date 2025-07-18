package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.anthropic.internal.api.AnthropicRole.USER;
import static java.util.Collections.singletonList;

public class AnthropicTokenCountEstimator implements TokenCountEstimator {
    private final AnthropicClient client;

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


    }

    @Override
    public int estimateTokenCountInText(final String text) {

        AnthropicCreateMessageRequest request = AnthropicCreateMessageRequest.builder()
                .model("claude-2")
                .messages(singletonList(new AnthropicMessage(USER, singletonList(new AnthropicTextContent(text)))))
                .build();

        client.createMessage(request); // response is not going to be valid
        return 0;
    }

    @Override
    public int estimateTokenCountInMessage(final ChatMessage message) {
        return 0;
    }

    @Override
    public int estimateTokenCountInMessages(final Iterable<ChatMessage> messages) {
        return 0;
    }

    public static class Builder {
        public HttpClientBuilder httpClientBuilder;
        public String baseUrl;
        public String apiKey;
        public String version;
        public String beta;
        public Duration timeout;
        public Boolean logRequests;
        public Boolean logResponses;

        Builder() {}

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

        public AnthropicTokenCountEstimator build() {
            return new AnthropicTokenCountEstimator(this);
        }
    }
}
