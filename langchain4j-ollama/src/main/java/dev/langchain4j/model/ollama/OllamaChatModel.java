package dev.langchain4j.model.ollama;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.ollama.spi.OllamaChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.createModelListenerRequest;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenError;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenRequest;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenResponse;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaMessages;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaResponseFormat;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaTools;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toToolExecutionRequests;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaChatModel implements ChatLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final ResponseFormat responseFormat;
    private final Integer maxRetries;
    private final List<ChatModelListener> listeners;
    private final Set<Capability> supportedCapabilities;

    public OllamaChatModel(HttpClientBuilder httpClientBuilder,
                           String baseUrl,
                           String modelName,
                           Double temperature,
                           Integer topK,
                           Double topP,
                           Double repeatPenalty,
                           Integer seed,
                           Integer numPredict,
                           Integer numCtx,
                           List<String> stop,
                           String format,
                           ResponseFormat responseFormat,
                           Duration timeout,
                           Integer maxRetries,
                           Map<String, String> customHeaders,
                           Boolean logRequests,
                           Boolean logResponses,
                           List<ChatModelListener> listeners,
                           Set<Capability> supportedCapabilities) {

        if (format != null && responseFormat != null) {
            throw new IllegalStateException("Cant use both 'format' and 'responseFormat' parameters");
        }

        this.client = OllamaClient.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .customHeaders(customHeaders)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();

        this.modelName = ensureNotBlank(modelName, "modelName");
        this.options = Options.builder()
                .temperature(temperature)
                .topK(topK)
                .topP(topP)
                .repeatPenalty(repeatPenalty)
                .seed(seed)
                .numPredict(numPredict)
                .numCtx(numCtx)
                .stop(stop)
                .build();
        this.responseFormat = "json".equals(format) ? ResponseFormat.JSON : responseFormat;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.listeners = new ArrayList<>(getOrDefault(listeners, emptyList()));
        this.supportedCapabilities = new HashSet<>(getOrDefault(supportedCapabilities, emptySet()));
    }

    public static OllamaChatModelBuilder builder() {
        for (OllamaChatModelBuilderFactory factory : loadFactories(OllamaChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaChatModelBuilder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");

        return doGenerate(messages, null, responseFormat);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ensureNotEmpty(messages, "messages");

        return doGenerate(messages, toolSpecifications, responseFormat);
    }

    @Override
    public dev.langchain4j.model.chat.response.ChatResponse chat(dev.langchain4j.model.chat.request.ChatRequest request) {

        ChatRequestParameters parameters = request.parameters();
        ChatLanguageModel.validate(parameters);
        ChatLanguageModel.validate(parameters.toolChoice());

        Response<AiMessage> response = doGenerate(
                request.messages(),
                request.toolSpecifications(),
                getOrDefault(request.responseFormat(), this.responseFormat)
        );

        return dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    private Response<AiMessage> doGenerate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, ResponseFormat responseFormat) {
        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(toOllamaResponseFormat(responseFormat))
                .stream(false)
                .tools(toOllamaTools(toolSpecifications))
                .build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(request, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        onListenRequest(listeners, modelListenerRequest, attributes);

        try {
            ChatResponse chatResponse = withRetry(() -> client.chat(request), maxRetries);
            Response<AiMessage> response = Response.from(
                    chatResponse.getMessage().getToolCalls() != null ?
                            AiMessage.from(toToolExecutionRequests(chatResponse.getMessage().getToolCalls())) :
                            AiMessage.from(chatResponse.getMessage().getContent()),
                    new TokenUsage(chatResponse.getPromptEvalCount(), chatResponse.getEvalCount())
            );
            onListenResponse(listeners, response, modelListenerRequest, attributes);

            return response;
        } catch (Exception e) {
            onListenError(listeners, e, modelListenerRequest, null, attributes);
            throw e;
        }
    }

    public static class OllamaChatModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String modelName;
        private Double temperature;
        private Integer topK;
        private Double topP;
        private Double repeatPenalty;
        private Integer seed;
        private Integer numPredict;
        private Integer numCtx;
        private List<String> stop;
        private String format;
        private ResponseFormat responseFormat;
        private Duration timeout;
        private Integer maxRetries;
        private Map<String, String> customHeaders;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private Set<Capability> supportedCapabilities;

        public OllamaChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        /**
         * TODO
         * TODO {@link #timeout(Duration)} overrides timeouts set on the {@link HttpClientBuilder}
         *
         * @param httpClientBuilder
         * @return
         */
        public OllamaChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OllamaChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OllamaChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OllamaChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OllamaChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public OllamaChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public OllamaChatModelBuilder repeatPenalty(Double repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return this;
        }

        public OllamaChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OllamaChatModelBuilder numPredict(Integer numPredict) {
            this.numPredict = numPredict;
            return this;
        }

        public OllamaChatModelBuilder numCtx(Integer numCtx) {
            this.numCtx = numCtx;
            return this;
        }

        public OllamaChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        /**
         * @deprecated Please use {@link #responseFormat(ResponseFormat)} instead.
         * For example: {@code responseFormat(ResponseFormat.JSON)}.
         * <br>
         * Instead of using JSON mode, consider using structured outputs with JSON schema instead,
         * see more info <a href="https://docs.langchain4j.dev/tutorials/structured-outputs#json-schema">here</a>.
         */
        @Deprecated
        public OllamaChatModelBuilder format(String format) {
            this.format = format;
            return this;
        }

        public OllamaChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OllamaChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OllamaChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OllamaChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OllamaChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OllamaChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OllamaChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OllamaChatModelBuilder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public OllamaChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
        }

        public OllamaChatModel build() {
            return new OllamaChatModel(
                    httpClientBuilder,
                    baseUrl,
                    modelName,
                    temperature,
                    topK,
                    topP,
                    repeatPenalty,
                    seed,
                    numPredict,
                    numCtx,
                    stop,
                    format,
                    responseFormat,
                    timeout,
                    maxRetries,
                    customHeaders,
                    logRequests,
                    logResponses,
                    listeners,
                    supportedCapabilities
            );
        }
    }
}
