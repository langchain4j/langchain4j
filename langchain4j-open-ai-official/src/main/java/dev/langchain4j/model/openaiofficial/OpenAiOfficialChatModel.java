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
import java.util.StringJoiner;

public class OpenAiOfficialChatModel extends OpenAiOfficialBaseChatModel
        implements ChatLanguageModel, TokenCountEstimator {

    public OpenAiOfficialChatModel(
            String baseUrl,
            String apiKey,
            String azureApiKey,
            Credential credential,
            String azureDeploymentName,
            AzureOpenAIServiceVersion azureOpenAIServiceVersion,
            String organizationId,
            ChatRequestParameters defaultRequestParameters,
            String modelName,
            Double temperature,
            Double topP,
            List<String> stop,
            Integer maxCompletionTokens,
            Double presencePenalty,
            Double frequencyPenalty,
            Map<String, Integer> logitBias,
            String responseFormat,
            Boolean strictJsonSchema,
            Integer seed,
            String user,
            Boolean strictTools,
            Boolean parallelToolCalls,
            Boolean store,
            Map<String, String> metadata,
            String serviceTier,
            Duration timeout,
            Integer maxRetries,
            Proxy proxy,
            Tokenizer tokenizer,
            Map<String, String> customHeaders,
            List<ChatModelListener> listeners,
            Set<Capability> capabilities) {

        init(
                baseUrl,
                apiKey,
                azureApiKey,
                credential,
                azureDeploymentName,
                azureOpenAIServiceVersion,
                organizationId,
                defaultRequestParameters,
                modelName,
                temperature,
                topP,
                stop,
                maxCompletionTokens,
                presencePenalty,
                frequencyPenalty,
                logitBias,
                responseFormat,
                strictJsonSchema,
                seed,
                user,
                strictTools,
                parallelToolCalls,
                store,
                metadata,
                serviceTier,
                timeout,
                maxRetries,
                proxy,
                tokenizer,
                customHeaders,
                listeners,
                capabilities);
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

    public static OpenAiOfficialChatModelBuilder builder() {
        for (OpenAiOfficialChatModelBuilderFactory factory :
                loadFactories(OpenAiOfficialChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiOfficialChatModelBuilder();
    }

    public static class OpenAiOfficialChatModelBuilder {

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

        public OpenAiOfficialChatModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets default common {@link ChatRequestParameters} or OpenAI-specific {@link OpenAiOfficialChatRequestParameters}.
         * <br>
         * When a parameter is set via an individual builder method (e.g., {@link #modelName(String)}),
         * its value takes precedence over the same parameter set via {@link ChatRequestParameters}.
         */
        public OpenAiOfficialChatModelBuilder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        public OpenAiOfficialChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiOfficialChatModelBuilder modelName(ChatModel modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiOfficialChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiOfficialChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiOfficialChatModelBuilder azureApiKey(String azureApiKey) {
            this.azureApiKey = azureApiKey;
            return this;
        }

        public OpenAiOfficialChatModelBuilder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        public OpenAiOfficialChatModelBuilder azureDeploymentName(String azureDeploymentName) {
            this.azureDeploymentName = azureDeploymentName;
            return this;
        }

        public OpenAiOfficialChatModelBuilder azureOpenAIServiceVersion(
                AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public OpenAiOfficialChatModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiOfficialChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OpenAiOfficialChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public OpenAiOfficialChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public OpenAiOfficialChatModelBuilder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        public OpenAiOfficialChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public OpenAiOfficialChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public OpenAiOfficialChatModelBuilder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public OpenAiOfficialChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OpenAiOfficialChatModelBuilder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public OpenAiOfficialChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OpenAiOfficialChatModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiOfficialChatModelBuilder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public OpenAiOfficialChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public OpenAiOfficialChatModelBuilder store(Boolean store) {
            this.store = store;
            return this;
        }

        public OpenAiOfficialChatModelBuilder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public OpenAiOfficialChatModelBuilder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public OpenAiOfficialChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiOfficialChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiOfficialChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public OpenAiOfficialChatModelBuilder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public OpenAiOfficialChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiOfficialChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OpenAiOfficialChatModelBuilder supportedCapabilities(Set<Capability> capabilities) {
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

            return new OpenAiOfficialChatModel(
                    this.baseUrl,
                    this.apiKey,
                    this.azureApiKey,
                    this.credential,
                    this.azureDeploymentName,
                    this.azureOpenAIServiceVersion,
                    this.organizationId,
                    this.defaultRequestParameters,
                    this.modelName,
                    this.temperature,
                    this.topP,
                    this.stop,
                    this.maxCompletionTokens,
                    this.presencePenalty,
                    this.frequencyPenalty,
                    this.logitBias,
                    this.responseFormat,
                    this.strictJsonSchema,
                    this.seed,
                    this.user,
                    this.strictTools,
                    this.parallelToolCalls,
                    this.store,
                    this.metadata,
                    this.serviceTier,
                    this.timeout,
                    this.maxRetries,
                    this.proxy,
                    this.tokenizer,
                    this.customHeaders,
                    this.listeners,
                    this.capabilities);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", OpenAiOfficialChatModelBuilder.class.getSimpleName() + "[", "]")
                    .add("baseUrl='" + baseUrl + "'")
                    .add("azureOpenAIServiceVersion=" + azureOpenAIServiceVersion)
                    .add("organizationId='" + organizationId + "'")
                    .add("defaultRequestParameters='" + defaultRequestParameters + "'")
                    .add("modelName='" + modelName + "'")
                    .add("temperature=" + temperature)
                    .add("topP=" + topP)
                    .add("stop=" + stop)
                    .add("maxCompletionTokens=" + maxCompletionTokens)
                    .add("presencePenalty=" + presencePenalty)
                    .add("frequencyPenalty=" + frequencyPenalty)
                    .add("logitBias=" + logitBias)
                    .add("responseFormat='" + responseFormat + "'")
                    .add("strictJsonSchema=" + strictJsonSchema)
                    .add("seed=" + seed)
                    .add("user='" + user + "'")
                    .add("strictTools=" + strictTools)
                    .add("parallelToolCalls=" + parallelToolCalls)
                    .add("store=" + store)
                    .add("metadata=" + metadata)
                    .add("serviceTier=" + serviceTier)
                    .add("timeout=" + timeout)
                    .add("maxRetries=" + maxRetries)
                    .add("proxy=" + proxy)
                    .add("tokenizer=" + tokenizer)
                    .add("customHeaders=" + customHeaders)
                    .add("listeners=" + listeners)
                    .add("capabilities=" + capabilities)
                    .toString();
        }
    }
}
