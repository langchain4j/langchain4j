package dev.langchain4j.model.openai.responses;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * OpenAI Responses API implementation using the official OpenAI Java SDK.
 *
 * This is a proof of concept demonstrating:
 * - Stateless mode operation (store=false with encrypted reasoning)
 * - Response chaining via previousResponseId
 * - Integration with langchain4j ChatModel interface
 *
 * Key POC requirement: Use official SDK instead of custom HTTP client.
 */
@Experimental
public class OpenAiResponsesChatModel implements ChatModel {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final OpenAIClient client;
    private final OpenAiResponsesChatRequestParameters defaultRequestParameters;

    private OpenAiResponsesChatModel(Builder builder) {
        this.client = setupClient(builder);
        this.defaultRequestParameters = buildDefaultParameters(builder);
    }

    private OpenAIClient setupClient(Builder builder) {
        if (builder.openAIClient != null) {
            return builder.openAIClient;
        }

        OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder();

        // Base URL
        String baseUrl = getOrDefault(builder.baseUrl, DEFAULT_BASE_URL);
        clientBuilder.baseUrl(baseUrl);

        // API Key
        String apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        clientBuilder.apiKey(apiKey);

        // Organization (optional)
        if (builder.organizationId != null) {
            clientBuilder.organization(builder.organizationId);
        }

        // Timeout
        Duration timeout = getOrDefault(builder.timeout, DEFAULT_TIMEOUT);
        clientBuilder.timeout(timeout);

        // Max retries
        Integer maxRetries = getOrDefault(builder.maxRetries, DEFAULT_MAX_RETRIES);
        clientBuilder.maxRetries(maxRetries);

        // Custom headers
        if (builder.customHeaders != null) {
            builder.customHeaders.forEach((key, value) ->
                clientBuilder.putHeader(key, value)
            );
        }

        return clientBuilder.build();
    }

    private OpenAiResponsesChatRequestParameters buildDefaultParameters(Builder builder) {
        OpenAiResponsesChatRequestParameters.Builder paramsBuilder = OpenAiResponsesChatRequestParameters.builder();

        // Model
        if (builder.modelName != null) {
            paramsBuilder.modelName(builder.modelName);
        }

        // Standard parameters
        if (builder.temperature != null) {
            paramsBuilder.temperature(builder.temperature);
        }
        if (builder.topP != null) {
            paramsBuilder.topP(builder.topP);
        }
        if (builder.maxCompletionTokens != null) {
            paramsBuilder.maxCompletionTokens(builder.maxCompletionTokens);
        }
        if (builder.seed != null) {
            paramsBuilder.seed(builder.seed);
        }
        if (builder.user != null) {
            paramsBuilder.user(builder.user);
        }

        // Responses API specific parameters
        if (builder.instructions != null) {
            paramsBuilder.instructions(builder.instructions);
        }
        if (builder.reasoningEffort != null) {
            paramsBuilder.reasoningEffort(builder.reasoningEffort);
        }
        if (builder.metadata != null) {
            paramsBuilder.metadata(builder.metadata);
        }

        // Always use stateless mode (store=false)
        paramsBuilder.store(false);

        // Build include list based on flags
        List<String> include = new ArrayList<>();
        if (Boolean.TRUE.equals(builder.returnEncryptedReasoning)) {
            include.add("reasoning.encrypted_content");
        }
        if (!include.isEmpty()) {
            paramsBuilder.include(include);
        }

        return paramsBuilder.build();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        // Merge default parameters with request parameters
        // This ensures model-level settings (like reasoningEffort) are applied
        OpenAiResponsesChatRequestParameters parameters =
            (OpenAiResponsesChatRequestParameters) defaultRequestParameters
                .overrideWith(chatRequest.parameters());

        // Convert to SDK parameters
        ResponseCreateParams.Builder paramsBuilder = InternalResponsesHelper.toResponseCreateParams(
            chatRequest, parameters);

        ResponseCreateParams params = paramsBuilder.build();

        // Call the official SDK (KEY POC REQUIREMENT)
        Response response = client.responses().create(params);

        // Convert back to langchain4j response
        return InternalResponsesHelper.toChatResponse(response);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String apiKey;
        private String baseUrl;
        private String organizationId;
        private Duration timeout;
        private Integer maxRetries;
        private Map<String, String> customHeaders;
        private OpenAIClient openAIClient;

        // Model configuration
        private String modelName = "gpt-4o";
        private Double temperature;
        private Double topP;
        private Integer maxCompletionTokens;
        private Integer seed;
        private String user;

        // Responses API specific
        private String instructions;
        private String reasoningEffort;
        private Boolean returnReasoningSummary;
        private Boolean returnEncryptedReasoning;
        private Map<String, String> metadata;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
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

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder openAIClient(OpenAIClient openAIClient) {
            this.openAIClient = openAIClient;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
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

        public Builder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        /**
         * Set the reasoning effort level for models that support reasoning.
         * Common values: "low", "medium", "high"
         */
        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        /**
         * Controls whether to return reasoning summary text in the response.
         * Default: false
         */
        public Builder returnReasoningSummary(Boolean returnReasoningSummary) {
            this.returnReasoningSummary = returnReasoningSummary;
            return this;
        }

        /**
         * Include encrypted reasoning tokens for multi-turn conversation chaining in stateless mode.
         * Only supported by certain models (e.g., o1 series).
         * Default: false
         */
        public Builder returnEncryptedReasoning(Boolean returnEncryptedReasoning) {
            this.returnEncryptedReasoning = returnEncryptedReasoning;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public OpenAiResponsesChatModel build() {
            return new OpenAiResponsesChatModel(this);
        }
    }
}
