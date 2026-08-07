package dev.langchain4j.model.deepseek;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.emptyList;

/**
 * Represents a DeepSeek language model with a chat completion interface.
 * <p>
 * DeepSeek's API is OpenAI-compatible, so this model wraps {@link OpenAiChatModel}
 * with pre-configured DeepSeek defaults.
 * <p>
 * Usage:
 * <pre>{@code
 * DeepSeekChatModel model = DeepSeekChatModel.builder()
 *         .apiKey("sk-xxx")
 *         .modelName(DeepSeekChatModelName.DEEPSEEK_CHAT)
 *         .build();
 * }</pre>
 *
 * @see <a href="https://api-docs.deepseek.com/">DeepSeek API Documentation</a>
 */
public class DeepSeekChatModel implements ChatModel {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";

    private final OpenAiChatModel delegate;
    private final String modelName;

    private DeepSeekChatModel(Builder builder) {
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.delegate = OpenAiChatModel.builder()
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(builder.apiKey)
                .modelName(builder.modelName)
                .temperature(builder.temperature)
                .topP(builder.topP)
                .maxTokens(builder.maxTokens)
                .maxRetries(builder.maxRetries)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .listeners(builder.listeners)
                .customHeaders(builder.customHeaders)
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        return delegate.chat(chatRequest);
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    public String modelName() {
        return modelName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Integer maxRetries;
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;
        private List<ChatModelListener> listeners;
        private Map<String, String> customHeaders;

        /**
         * Sets the DeepSeek API base URL. Default is "https://api.deepseek.com/v1".
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the DeepSeek API key. Required.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the model name. Use {@link DeepSeekChatModelName} constants.
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the model name using the enum.
         */
        public Builder modelName(DeepSeekChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public DeepSeekChatModel build() {
            return new DeepSeekChatModel(this);
        }
    }
}
