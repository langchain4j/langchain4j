package dev.langchain4j.model.openaiofficial;

import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.convertHandler;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.finishReasonFrom;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.toOpenAiChatCompletionCreateParams;
import static dev.langchain4j.model.openaiofficial.InternalOpenAiOfficialHelper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.core.http.StreamResponse;
import com.openai.credential.Credential;
import com.openai.models.ChatCompletionChunk;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionStreamOptions;
import com.openai.models.ChatModel;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openaiofficial.spi.OpenAiOfficialStreamingChatModelBuilderFactory;
import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

public class OpenAiOfficialStreamingChatModel extends OpenAiOfficialBaseChatModel
        implements StreamingChatLanguageModel, TokenCountEstimator {

    public OpenAiOfficialStreamingChatModel(
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
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();
        chat(chatRequest, convertHandler(handler));
    }

    @Override
    public void generate(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
        chat(chatRequest, convertHandler(handler));
    }

    @Override
    public void generate(
            List<ChatMessage> messages,
            ToolSpecification toolSpecification,
            StreamingResponseHandler<AiMessage> handler) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .toolSpecifications(toolSpecification)
                        .toolChoice(REQUIRED)
                        .build())
                .build();
        chat(chatRequest, convertHandler(handler));
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

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
                .streamOptions(
                        ChatCompletionStreamOptions.builder().includeUsage(true).build())
                .build();

        try (StreamResponse<ChatCompletionChunk> response =
                client.chat().completions().createStreaming(chatCompletionCreateParams)) {

            OpenAiOfficialChatResponseMetadata.Builder responseMetadataBuilder =
                    OpenAiOfficialChatResponseMetadata.builder();
            StringBuffer text = new StringBuffer();
            List<ToolExecutionRequest> toolExecutionRequests = new ArrayList<>();

            response.stream().forEach(chatCompletionChunk -> {
                responseMetadataBuilder.id(chatCompletionChunk.id());
                responseMetadataBuilder.modelName(chatCompletionChunk.model());
                if (chatCompletionChunk.usage().isPresent()) {
                    responseMetadataBuilder.tokenUsage(
                            tokenUsageFrom(chatCompletionChunk.usage().get()));
                }
                responseMetadataBuilder.created(chatCompletionChunk.created());
                responseMetadataBuilder.serviceTier(
                        chatCompletionChunk.serviceTier().isPresent()
                                ? chatCompletionChunk.serviceTier().get().toString()
                                : null);
                responseMetadataBuilder.systemFingerprint(
                        chatCompletionChunk.systemFingerprint().isPresent()
                                ? chatCompletionChunk.systemFingerprint().get()
                                : null);
                chatCompletionChunk.choices().forEach(choice -> {
                    if (choice.delta().content().isPresent()
                            && !choice.delta().content().get().isEmpty()) {
                        text.append(choice.delta().content().get());
                        handler.onPartialResponse(choice.delta().content().get());
                    }
                    if (choice.delta().toolCalls().isPresent()) {
                        choice.delta().toolCalls().get().forEach(toolCall -> {
                            if (toolCall.function().isPresent()) {
                                final ChatCompletionChunk.Choice.Delta.ToolCall.Function function =
                                        toolCall.function().get();
                                final String functionId;
                                final String functionName;
                                final String functionArguments;
                                if (toolCall.id().isPresent()) {
                                    functionId = toolCall.id().get();
                                } else {
                                    functionId = "";
                                }
                                if (function.name().isPresent()) {
                                    functionName = function.name().get();
                                } else {
                                    functionName = "";
                                }
                                if (function.arguments().isPresent()) {
                                    functionArguments = function.arguments().get();
                                } else {
                                    functionArguments = "";
                                }
                                if (!functionId.isEmpty()) {
                                    // A new function is called
                                    toolExecutionRequests.add(ToolExecutionRequest.builder()
                                            .id(functionId)
                                            .name(functionName)
                                            .arguments(functionArguments)
                                            .build());
                                } else {
                                    // This chunk is part of the previous function call
                                    ToolExecutionRequest lastToolExecutionRequest =
                                            toolExecutionRequests.get(toolExecutionRequests.size() - 1);
                                    toolExecutionRequests.remove(lastToolExecutionRequest);
                                    ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                                            .id(functionId)
                                            .name(lastToolExecutionRequest.name() + functionName)
                                            .arguments(lastToolExecutionRequest.arguments() + functionArguments)
                                            .build();
                                    toolExecutionRequests.add(toolExecutionRequest);
                                }
                            }
                        });
                    }
                    if (choice.finishReason().isPresent()) {
                        responseMetadataBuilder.finishReason(
                                finishReasonFrom(choice.finishReason().get()));
                    }
                });
            });

            AiMessage aiMessage;
            if (text.toString().isEmpty() && !toolExecutionRequests.isEmpty()) {
                aiMessage = AiMessage.from(toolExecutionRequests);
            } else if (!text.toString().isEmpty() && toolExecutionRequests.isEmpty()) {
                aiMessage = AiMessage.from(text.toString());
            } else if (!text.toString().isEmpty()) {
                aiMessage = AiMessage.from(text.toString(), toolExecutionRequests);
            } else {
                aiMessage = AiMessage.from();
            }

            ChatResponse chatResponse = ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(responseMetadataBuilder.build())
                    .build();

            handler.onCompleteResponse(chatResponse);
        } catch (Exception e) {
            handler.onError(e);
        }
    }

    public static OpenAiOfficialStreamingChatModelBuilder builder() {
        for (OpenAiOfficialStreamingChatModelBuilderFactory factory :
                loadFactories(OpenAiOfficialStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiOfficialStreamingChatModelBuilder();
    }

    public static class OpenAiOfficialStreamingChatModelBuilder {

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

        public OpenAiOfficialStreamingChatModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets default common {@link ChatRequestParameters} or OpenAI-specific {@link OpenAiOfficialChatRequestParameters}.
         * <br>
         * When a parameter is set via an individual builder method (e.g., {@link #modelName(String)}),
         * its value takes precedence over the same parameter set via {@link ChatRequestParameters}.
         */
        public OpenAiOfficialStreamingChatModelBuilder defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder modelName(ChatModel modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder azureApiKey(String azureApiKey) {
            this.azureApiKey = azureApiKey;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder credential(Credential credential) {
            this.credential = credential;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder azureDeploymentName(String azureDeploymentName) {
            this.azureDeploymentName = azureDeploymentName;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder azureOpenAIServiceVersion(
                AzureOpenAIServiceVersion azureOpenAIServiceVersion) {
            this.azureOpenAIServiceVersion = azureOpenAIServiceVersion;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder maxCompletionTokens(Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder logitBias(Map<String, Integer> logitBias) {
            this.logitBias = logitBias;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder strictJsonSchema(Boolean strictJsonSchema) {
            this.strictJsonSchema = strictJsonSchema;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder store(Boolean store) {
            this.store = store;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder serviceTier(String serviceTier) {
            this.serviceTier = serviceTier;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OpenAiOfficialStreamingChatModelBuilder supportedCapabilities(Set<Capability> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public OpenAiOfficialStreamingChatModel build() {

            if (azureApiKey != null || credential != null) {
                // Using Azure OpenAI
                if (this.defaultRequestParameters != null && this.defaultRequestParameters.modelName() != null) {
                    if (!this.defaultRequestParameters.modelName().equals(this.modelName)) {
                        throw new UnsupportedFeatureException(
                                "On Azure OpenAI, it is not supported to change the modelName, as it's part of the deployment URL");
                    }
                }
            }

            return new OpenAiOfficialStreamingChatModel(
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
            return new StringJoiner(", ", OpenAiOfficialStreamingChatModelBuilder.class.getSimpleName() + "[", "]")
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
