package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.aiMessageFrom;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.convertResponse;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.finishReasonFrom;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.toOpenAiChatCompletionCreateParams;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.credential.Credential;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatModel;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.spi.OpenAiOfficialChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OpenAiOfficialChatModel extends OpenAiOfficialBaseChatModel
        implements ChatLanguageModel, TokenCountEstimator {

    public OpenAiOfficialChatModel(Builder builder) {

        init(
                builder.baseUrl,
                builder.apiKey,
                builder.azureApiKey,
                builder.credential,
                builder.azureDeploymentName,
                builder.azureOpenAIServiceVersion,
                builder.organizationId,
                builder.defaultRequestParameters,
                builder.modelName,
                builder.temperature,
                builder.topP,
                builder.stop,
                builder.maxCompletionTokens,
                builder.presencePenalty,
                builder.frequencyPenalty,
                builder.logitBias,
                builder.responseFormat,
                builder.strictJsonSchema,
                builder.seed,
                builder.user,
                builder.strictTools,
                builder.parallelToolCalls,
                builder.store,
                builder.metadata,
                builder.serviceTier,
                builder.timeout,
                builder.maxRetries,
                builder.proxy,
                builder.tokenizer,
                builder.customHeaders,
                builder.listeners,
                builder.capabilities);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();
        ChatResponse chatResponse = chat(chatRequest);
        return convertResponse(chatResponse);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
        ChatResponse chatResponse = chat(chatRequest);
        return convertResponse(chatResponse);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .toolChoice(REQUIRED)
                        .build())
                .build();
        ChatResponse chatResponse = chat(chatRequest);
        return convertResponse(chatResponse);
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {

        OpenAiOfficialChatRequestParameters parameters = (OpenAiOfficialChatRequestParameters) chatRequest.parameters();
        InternalOpenAiOfficialHelper.validate(parameters);

        if (this.useAzure) {
            if (!parameters.modelName().equals(this.azureModelName)) {
                throw new UnsupportedFeatureException(
                        "On Azure OpenAI, it is not supported to change the modelName, as it's part of the deployment URL");
            }
        }

        ChatCompletionCreateParams chatCompletionCreateParams = toOpenAiChatCompletionCreateParams(
                        chatRequest, parameters, strictTools, strictJsonSchema)
                .build();

        // Unlike other LangChain4j modules, this doesn't use the `withRetry` method because the OpenAI SDK already has
        // retry logic included
        ChatCompletion chatCompletion = client.chat().completions().create(chatCompletionCreateParams);

        OpenAiOfficialChatResponseMetadata.Builder responseMetadataBuilder =
                OpenAiOfficialChatResponseMetadata.builder()
                        .id(chatCompletion.id())
                        .modelName(chatCompletion.model())
                        .created(chatCompletion.created());

        if (!chatCompletion.choices().isEmpty()) {
            responseMetadataBuilder.finishReason(
                    finishReasonFrom(chatCompletion.choices().get(0).finishReason()));
        }
        if (chatCompletion.usage().isPresent()) {
            responseMetadataBuilder.tokenUsage(
                    tokenUsageFrom(chatCompletion.usage().get()));
        }
        if (chatCompletion.serviceTier().isPresent()) {
            responseMetadataBuilder.serviceTier(
                    chatCompletion.serviceTier().get().toString());
        }
        if (chatCompletion.systemFingerprint().isPresent()) {
            responseMetadataBuilder.systemFingerprint(
                    chatCompletion.systemFingerprint().get());
        }

        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(chatCompletion))
                .metadata(responseMetadataBuilder.build())
                .build();
    }

    public static Builder builder() {
        for (OpenAiOfficialChatModelBuilderFactory factory :
                loadFactories(OpenAiOfficialChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private String azureApiKey;
        private Credential credential;
        private String azureDeploymentName;
        private AzureOpenAIServiceVersion azureOpenAIServiceVersion;
        private String organizationId;

        private ChatRequestParameters defaultRequestParameters;
        private String modelName;
        private Double temperature;
        private Double topP;
        private List<String> stop;
        private Integer maxCompletionTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Map<String, Integer> logitBias;
        private String responseFormat;
        private Boolean strictJsonSchema;
        private Integer seed;
        private String user;
        private Boolean strictTools;
        private Boolean parallelToolCalls;
        private Boolean store;
        private Map<String, String> metadata;
        private String serviceTier;

        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private Tokenizer tokenizer;
        private Map<String, String> customHeaders;
        private List<ChatModelListener> listeners;
        private Set<Capability> capabilities;

        public Builder() {
            // This is public so it can be extended
        }

        /**
         * Sets default common {@link ChatRequestParameters} or OpenAI-specific {@link OpenAiOfficialChatRequestParameters}.
         * <br>
         * When a parameter is set via an individual builder method (e.g., {@link #modelName(String)}),
         * its value takes precedence over the same parameter set via {@link ChatRequestParameters}.
         */
        public Builder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelName(ChatModel modelName) {
            this.modelName = modelName.toString();
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

        public Builder azureApiKey(String azureApiKey) {
            this.azureApiKey = azureApiKey;
            return this;
        }

        public Builder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        public Builder azureDeploymentName(String azureDeploymentName) {
            this.azureDeploymentName = azureDeploymentName;
            return this;
        }

        public Builder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public Builder organizationId(String organizationId) {
            this.organizationId = organizationId;
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

        public Builder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public Builder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
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

        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder store(Boolean store) {
            this.store = store;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
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

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder supportedCapabilities(Set<Capability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public OpenAiOfficialChatModel build() {
            if (azureApiKey != null || credential != null) {
                // Using Azure OpenAI
                if (this.defaultRequestParameters != null && this.defaultRequestParameters.modelName() != null) {
                    if (!this.defaultRequestParameters.modelName().equals(this.modelName)) {
                        throw new UnsupportedFeatureException(
                                "On Azure OpenAI, it is not supported to change the modelName, as it's part of the deployment URL");
                    }
                }
            }
            return new OpenAiOfficialChatModel(this);
        }
    }
}
