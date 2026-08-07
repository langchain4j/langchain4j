package dev.langchain4j.model.deepseek;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Represents a DeepSeek language model with a streaming chat completion interface.
 * <p>
 * Usage:
 * <pre>{@code
 * DeepSeekStreamingChatModel model = DeepSeekStreamingChatModel.builder()
 *         .apiKey("sk-xxx")
 *         .modelName(DeepSeekChatModelName.DEEPSEEK_CHAT)
 *         .build();
 * }</pre>
 *
 * @see <a href="https://api-docs.deepseek.com/">DeepSeek API Documentation</a>
 */
public class DeepSeekStreamingChatModel implements StreamingChatModel {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";

    private final OpenAiStreamingChatModel delegate;

    private DeepSeekStreamingChatModel(Builder builder) {
        ensureNotBlank(builder.modelName, "modelName");
        this.delegate = OpenAiStreamingChatModel.builder()
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(builder.apiKey)
                .modelName(builder.modelName)
                .temperature(builder.temperature)
                .topP(builder.topP)
                .maxTokens(builder.maxTokens)
                .timeout(builder.timeout)
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .listeners(builder.listeners)
                .customHeaders(builder.customHeaders)
                .build();
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        delegate.chat(request, handler);
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
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
        private Duration timeout;
        private boolean logRequests;
        private boolean logResponses;
        private List<ChatModelListener> listeners;
        private Map<String, String> customHeaders;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

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

        public DeepSeekStreamingChatModel build() {
            return new DeepSeekStreamingChatModel(this);
        }
    }
}
