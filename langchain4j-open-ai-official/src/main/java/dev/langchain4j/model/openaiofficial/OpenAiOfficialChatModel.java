package dev.langchain4j.model.openaiofficial;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
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
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.spi.OpenAiOfficialChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.DEFAULT_USER_AGENT;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.OPENAI_DEMO_API_KEY;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.OPENAI_DEMO_URL;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.OPENAI_URL;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.aiMessageFrom;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.convertResponse;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.finishReasonFrom;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.fromOpenAiResponseFormat;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.toOpenAiChatCompletionCreateParams;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;

public class OpenAiOfficialChatModel implements ChatLanguageModel, TokenCountEstimator {

    private final OpenAIClient client;
    private final boolean useAzure;
    private final String azureModelName;

    private final OpenAiOfficialChatRequestParameters defaultRequestParameters;
    private final String responseFormat;
    private final Boolean strictJsonSchema;
    private final Boolean strictTools;

    private final Tokenizer tokenizer;

    private final List<ChatModelListener> listeners;
    private final Set<Capability> supportedCapabilities;

    public OpenAiOfficialChatModel(String baseUrl,
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

        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();

        baseUrl = getOrDefault(baseUrl, OPENAI_URL);
        if (OPENAI_DEMO_API_KEY.equals(apiKey)) {
            baseUrl = OPENAI_DEMO_URL;
        }

        if (azureApiKey != null || credential != null) {
            // Using Azure OpenAI
            this.useAzure = true;
            ensureNotBlank(modelName, "modelName");
            this.azureModelName = modelName;
            // If the Azure deployment name is not configured, we use the model name instead, as it's the default deployment name
            if (azureDeploymentName == null) {
                azureDeploymentName = modelName;
            }
            ensureNotBlank(azureDeploymentName, "azureDeploymentName");
            ensureNotNull(azureOpenAIServiceVersion, "azureOpenAIServiceVersion");
            baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            builder.baseUrl(baseUrl + "/openai/deployments/" + azureDeploymentName + "?api-version=" + azureOpenAIServiceVersion.value());
        } else {
            // Using OpenAI
            this.useAzure = false;
            this.azureModelName = null;
            builder.baseUrl(baseUrl);
        }

        if (apiKey != null) {
            builder.apiKey(apiKey);
        } else if (azureApiKey != null) {
            builder.credential(AzureApiKeyCredential.create(azureApiKey));
        } else if (credential != null) {
            builder.credential(credential);
        } else {
            throw new IllegalArgumentException("Either apiKey, azureApiKey or credential must be set to authenticate");
        }

        builder.organization(organizationId);

        if (azureOpenAIServiceVersion != null) {
            builder.azureServiceVersion(azureOpenAIServiceVersion);
        }

        if (proxy != null) {
            builder.proxy(proxy);
        }

        if (customHeaders != null) {
            builder.putAllHeaders(customHeaders.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> Collections.singletonList(entry.getValue())
                    )));
        }
        builder.putHeader("User-Agent", DEFAULT_USER_AGENT);

        timeout = getOrDefault(timeout, ofSeconds(60));
        builder.timeout(timeout);

        builder.maxRetries(getOrDefault(maxRetries, 3));

        this.client = builder.build();

        ChatRequestParameters commonParameters;
        if (defaultRequestParameters != null) {
            commonParameters = defaultRequestParameters;
        } else {
            commonParameters = DefaultChatRequestParameters.builder().build();
        }

        OpenAiOfficialChatRequestParameters openAiParameters;
        if (defaultRequestParameters instanceof OpenAiOfficialChatRequestParameters openAiChatRequestParameters) {
            openAiParameters = openAiChatRequestParameters;
        } else {
            openAiParameters = OpenAiOfficialChatRequestParameters.builder().build();
        }

        this.defaultRequestParameters = OpenAiOfficialChatRequestParameters.builder()
                // common parameters
                .modelName(getOrDefault(modelName, commonParameters.modelName()))
                .temperature(getOrDefault(temperature, commonParameters.temperature()))
                .topP(getOrDefault(topP, commonParameters.topP()))
                .frequencyPenalty(getOrDefault(frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(presencePenalty, commonParameters.presencePenalty()))
                .stopSequences(getOrDefault(stop, () -> copyIfNotNull(commonParameters.stopSequences())))
                .toolSpecifications(copyIfNotNull(commonParameters.toolSpecifications()))
                .toolChoice(commonParameters.toolChoice())
                .responseFormat(getOrDefault(fromOpenAiResponseFormat(responseFormat), commonParameters.responseFormat()))
                // OpenAI-specific parameters
                .maxOutputTokens(getOrDefault(maxCompletionTokens, openAiParameters.maxOutputTokens()))
                .maxCompletionTokens(getOrDefault(maxCompletionTokens, openAiParameters.maxCompletionTokens()))
                .logitBias(getOrDefault(logitBias, () -> copyIfNotNull(openAiParameters.logitBias())))
                .parallelToolCalls(getOrDefault(parallelToolCalls, openAiParameters.parallelToolCalls()))
                .seed(getOrDefault(seed, openAiParameters.seed()))
                .user(getOrDefault(user, openAiParameters.user()))
                .store(getOrDefault(store, openAiParameters.store()))
                .metadata(getOrDefault(metadata, () -> copyIfNotNull(openAiParameters.metadata())))
                .serviceTier(getOrDefault(serviceTier, openAiParameters.serviceTier()))
                .reasoningEffort(openAiParameters.reasoningEffort())
                .build();
        this.responseFormat = responseFormat;
        this.strictJsonSchema = getOrDefault(strictJsonSchema, false); // TODO move into OpenAI-specific params?
        this.strictTools = getOrDefault(strictTools, false); // TODO move into OpenAI-specific params?

        this.tokenizer = getOrDefault(tokenizer, new OpenAiOfficialTokenizer(this.defaultRequestParameters.modelName()));

        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.supportedCapabilities = getOrDefault(copyIfNotNull(capabilities), new HashSet<>());
    }

    @Override
    public OpenAiOfficialChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        if ("json_schema".equals(responseFormat)) {
            this.supportedCapabilities.add(RESPONSE_FORMAT_JSON_SCHEMA);
        }
        return this.supportedCapabilities;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();
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
                throw new UnsupportedFeatureException("On Azure OpenAI, it is not supported to change the modelName, as it's part of the deployment URL");
            }
        }

        ChatCompletionCreateParams chatCompletionCreateParams =
                toOpenAiChatCompletionCreateParams(chatRequest, parameters, strictTools, strictJsonSchema).build();

        // Unlike other LangChain4j modules, this doesn't use the `withRetry` method because the OpenAI SDK already has retry logic included
        ChatCompletion chatCompletion = client.chat().completions().create(chatCompletionCreateParams);

        OpenAiOfficialChatResponseMetadata responseMetadata = OpenAiOfficialChatResponseMetadata.builder()
                .id(chatCompletion.id())
                .modelName(chatCompletion.model())
                .tokenUsage(tokenUsageFrom(chatCompletion.usage()))
                .finishReason(finishReasonFrom(chatCompletion.choices().get(0).finishReason()))
                .created(chatCompletion.created())
                .serviceTier(chatCompletion.serviceTier().isPresent() ? chatCompletion.serviceTier().get().toString() : null)
                .systemFingerprint(chatCompletion.systemFingerprint().isPresent() ? chatCompletion.systemFingerprint().get() : null)
                .build();

        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(chatCompletion))
                .metadata(responseMetadata)
                .build();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        return tokenizer.estimateTokenCountInMessages(messages);
    }

    public static OpenAiOfficialChatModelBuilder builder() {
        for (OpenAiOfficialChatModelBuilderFactory factory : loadFactories(OpenAiOfficialChatModelBuilderFactory.class)) {
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

        public OpenAiOfficialChatModelBuilder azureOpenAIServiceVersion(AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
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
                        throw new UnsupportedFeatureException("On Azure OpenAI, it is not supported to change the modelName, as it's part of the deployment URL");
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
                    this.capabilities
            );
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
