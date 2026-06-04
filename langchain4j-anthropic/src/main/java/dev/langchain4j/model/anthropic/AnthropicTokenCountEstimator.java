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

        return client.countTokens(request).getInputTokens();
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

        return client.countTokens(requestBuilder.build()).getInputTokens();
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

        /**
         * Sets a custom {@link HttpClientBuilder} for the underlying HTTP client.
         * Use this to configure timeouts, proxies, or other HTTP-level settings.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the base URL of the Anthropic API.
         * <p>
         * Defaults to {@code https://api.anthropic.com/v1/}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the Anthropic API key used to authenticate requests.
         * <p>
         * Alternatively, set the {@code ANTHROPIC_API_KEY} environment variable.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the value of the {@code anthropic-version} request header.
         * <p>
         * Defaults to {@code 2023-06-01}.
         * See the <a href="https://docs.anthropic.com/en/api/versioning">Anthropic API versioning docs</a>.
         *
         * @param version the API version string
         * @return {@code this}
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the value of the {@code anthropic-beta} request header to opt into beta features.
         * <p>
         * See the <a href="https://docs.anthropic.com/en/api/beta-headers">Anthropic beta headers docs</a>.
         *
         * @param beta the beta feature identifier
         * @return {@code this}
         */
        public Builder beta(String beta) {
            this.beta = beta;
            return this;
        }

        /**
         * Sets the HTTP request timeout for calls to the Anthropic API.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Enables debug logging of HTTP request bodies sent to the Anthropic API.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of HTTP response bodies received from the Anthropic API.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Sets the model used for token count estimation, specified as a string model ID.
         * <p>
         * This field is required.
         * See {@link AnthropicChatModelName} for available model constants.
         *
         * @param modelName the model ID, e.g. {@code "claude-opus-4-5"}
         * @return {@code this}
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the model used for token count estimation using a type-safe enum constant.
         * <p>
         * This field is required.
         *
         * @param modelName the model name enum value
         * @return {@code this}
         */
        public Builder modelName(AnthropicChatModelName modelName) {
            return modelName(modelName.toString());
        }

        /**
         * Configures the estimator to automatically insert a minimal dummy user message
         * ({@code "ping"}) when the provided messages contain only system messages or are empty.
         * <p>
         * The Anthropic token-counting API requires at least one non-system message.
         * Without this option, passing only system messages throws an {@link IllegalArgumentException}.
         *
         * @return {@code this}
         * @see #addDummyUserMessageIfNoUserMessages(String)
         */
        public Builder addDummyUserMessageIfNoUserMessages() {
            this.addDummyUserMessageIfNoUserMessages = true;
            return this;
        }

        /**
         * Configures the estimator to automatically insert a dummy user message with custom text
         * when the provided messages contain only system messages or are empty.
         * <p>
         * The Anthropic token-counting API requires at least one non-system message.
         * Without this option, passing only system messages throws an {@link IllegalArgumentException}.
         *
         * @param dummyUserMessage the text of the dummy user message to insert
         * @return {@code this}
         * @see #addDummyUserMessageIfNoUserMessages()
         */
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
