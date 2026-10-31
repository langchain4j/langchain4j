package dev.langchain4j.model.xai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A {@link LanguageModel} for xAI's Grok models: sends the prompt as a single user message to the
 * <a href="https://docs.x.ai/developers/model-capabilities/text/generate-text">xAI Responses API</a>.
 * Use {@link XAiChatModel} for multi-turn conversations, tools, structured outputs, etc.
 */
public class XAiLanguageModel implements LanguageModel {

    private final XAiClient client;
    private final XAiChatRequestParameters parameters;
    private final int maxRetries;

    private XAiLanguageModel(Builder builder) {
        this.client = XAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(builder.baseUrl)
                .apiKey(builder.apiKey)
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(15)))
                // reasoning models can think for a long time before answering
                .readTimeout(getOrDefault(builder.timeout, ofMinutes(10)))
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .customHeaders(builder.customHeadersSupplier)
                .build();
        this.parameters = XAiChatRequestParameters.builder()
                .modelName(ensureNotBlank(builder.modelName, "modelName"))
                .temperature(builder.temperature)
                .maxOutputTokens(builder.maxOutputTokens)
                .reasoningEffort(builder.reasoningEffort)
                .store(builder.store)
                .build();
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
    }

    public String modelName() {
        return parameters.modelName();
    }

    @Override
    public Response<String> generate(String prompt) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .parameters(parameters)
                .build();

        ChatResponse chatResponse = withRetryMappingExceptions(() -> client.chat(chatRequest, parameters), maxRetries);

        return Response.from(chatResponse.aiMessage().text(), chatResponse.tokenUsage(), chatResponse.finishReason());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Integer maxOutputTokens;
        private String reasoningEffort;
        private Boolean store;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Supplier<Map<String, String>> customHeadersSupplier;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Defaults to {@code https://api.x.ai/v1}.
         */
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

        public Builder modelName(XAiChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
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

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        public XAiLanguageModel build() {
            return new XAiLanguageModel(this);
        }
    }
}
