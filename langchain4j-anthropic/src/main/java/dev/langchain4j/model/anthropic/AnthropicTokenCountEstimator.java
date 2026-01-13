package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicSystemPrompt;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCountTokensRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicRole;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTextContent;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @since 1.4.0
 */
@Experimental
public class AnthropicTokenCountEstimator implements TokenCountEstimator {

    private final AnthropicClient client;
    private final String modelName;
    private final boolean addDummyUserMessageIfNoUserMessages;
    private final String dummyUserMessageText;

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
        this.addDummyUserMessageIfNoUserMessages = Boolean.TRUE.equals(builder.addDummyUserMessageIfNoUserMessages);
        this.dummyUserMessageText = getOrDefault(builder.dummyUserMessageText, "ping");
    }

    @Override
    public int estimateTokenCountInText(String text) {
        AnthropicCountTokensRequest request = AnthropicCountTokensRequest.builder()
                .model(this.modelName)
                .messages(List.of(new AnthropicMessage(AnthropicRole.USER, List.of(new AnthropicTextContent(text)))))
                .build();

        return client.countTokens(request).inputTokens;
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInMessages(List.of(message));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> otherMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.type() == ChatMessageType.SYSTEM) {
                systemMessages.add(message);
            } else {
                otherMessages.add(message);
            }
        }

        AnthropicCountTokensRequest.Builder requestBuilder =
                AnthropicCountTokensRequest.builder().model(this.modelName);

        if (!systemMessages.isEmpty()) {
            requestBuilder.system(toAnthropicSystemPrompt(systemMessages, AnthropicCacheType.NO_CACHE));
        }

        if (!otherMessages.isEmpty()) {
            requestBuilder.messages(toAnthropicMessages(otherMessages));
        } else if (addDummyUserMessageIfNoUserMessages) {
            requestBuilder.messages(List.of(
                    new AnthropicMessage(AnthropicRole.USER, List.of(new AnthropicTextContent(dummyUserMessageText)))));
        } else {
            throw new IllegalArgumentException("Anthropic countTokens requires at least one non-system message. "
                    + "Provided messages contained only system messages or were empty. To fix: add a UserMessage to "
                    + "your conversation, or configure AnthropicTokenCountEstimator.builder().addDummyUserMessageIfNoUserMessages() "
                    + "to auto-insert a minimal dummy user message for token estimation.");
        }

        return client.countTokens(requestBuilder.build()).inputTokens;
    }

    public static Builder builder() {
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
        private Boolean addDummyUserMessageIfNoUserMessages;
        private String dummyUserMessageText;

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

        public Builder modelName(AnthropicChatModelName modelName) {
            return modelName(modelName.toString());
        }

        public Builder addDummyUserMessageIfNoUserMessages() {
            this.addDummyUserMessageIfNoUserMessages = true;
            return this;
        }

        public Builder addDummyUserMessageIfNoUserMessages(String dummyUserMessage) {
            this.addDummyUserMessageIfNoUserMessages = true;
            this.dummyUserMessageText = dummyUserMessage;
            return this;
        }

        public AnthropicTokenCountEstimator build() {
            return new AnthropicTokenCountEstimator(this);
        }
    }
}
