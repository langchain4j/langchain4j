package dev.langchain4j.model.ollama;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.internal.ChatRequestValidationUtils;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.ollama.spi.OllamaStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.ModelProvider.OLLAMA;
import static dev.langchain4j.internal.ChatRequestValidationUtils.validate;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaMessages;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaResponseFormat;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaTools;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaStreamingChatModel implements StreamingChatModel {

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final ResponseFormat responseFormat;
    private final List<ChatModelListener> listeners;
    private final Set<Capability> supportedCapabilities;

    public OllamaStreamingChatModel(HttpClientBuilder httpClientBuilder,
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
                                    Boolean logRequests,
                                    Boolean logResponses,
                                    Map<String, String> customHeaders,
                                    List<ChatModelListener> listeners,
                                    Set<Capability> supportedCapabilities
    ) {
        if (format != null && responseFormat != null) {
            throw new IllegalStateException("Cant use both 'format' and 'responseFormat' parameters");
        }

        this.client = OllamaClient.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .customHeaders(customHeaders)
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
        this.listeners = new ArrayList<>(getOrDefault(listeners, emptyList()));
        this.supportedCapabilities = new HashSet<>(getOrDefault(supportedCapabilities, emptySet()));
    }

    @Override
    public void doChat(dev.langchain4j.model.chat.request.ChatRequest chatRequest, StreamingChatResponseHandler handler) {

        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidationUtils.validateParameters(parameters);
        ChatRequestValidationUtils.validate(parameters.toolChoice());
        validate(parameters.responseFormat());

        StreamingResponseHandler<AiMessage> legacyHandler = new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                handler.onPartialResponse(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                dev.langchain4j.model.chat.response.ChatResponse chatResponse =
                        dev.langchain4j.model.chat.response.ChatResponse.builder()
                                .aiMessage(response.content())
                                .metadata(ChatResponseMetadata.builder()
                                        .tokenUsage(response.tokenUsage())
                                        .finishReason(response.finishReason())
                                        .build())
                                .build();
                handler.onCompleteResponse(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            generate(chatRequest.messages(), legacyHandler);
        } else {
            generate(chatRequest.messages(), toolSpecifications, legacyHandler);
        }
    }

    private void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");

        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(toOllamaResponseFormat(responseFormat))
                .stream(true)
                .build();

        client.streamingChat(request, handler, listeners, provider(), messages);
    }

    private void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");

        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(toOllamaResponseFormat(responseFormat))
                .tools(toOllamaTools(toolSpecifications))
                .stream(true)
                .build();

        client.streamingChat(request, handler, listeners, provider(), messages);
    }

    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public ModelProvider provider() {
        return OLLAMA;
    }

    public static OllamaStreamingChatModelBuilder builder() {
        for (OllamaStreamingChatModelBuilderFactory factory : loadFactories(OllamaStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OllamaStreamingChatModelBuilder();
    }

    public static class OllamaStreamingChatModelBuilder {

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
        private Map<String, String> customHeaders;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private Set<Capability> supportedCapabilities;

        public OllamaStreamingChatModelBuilder() {
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
        public OllamaStreamingChatModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OllamaStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OllamaStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OllamaStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OllamaStreamingChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public OllamaStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public OllamaStreamingChatModelBuilder repeatPenalty(Double repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return this;
        }

        public OllamaStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public OllamaStreamingChatModelBuilder numPredict(Integer numPredict) {
            this.numPredict = numPredict;
            return this;
        }

        public OllamaStreamingChatModelBuilder numCtx(Integer numCtx) {
            this.numCtx = numCtx;
            return this;
        }

        public OllamaStreamingChatModelBuilder stop(List<String> stop) {
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
        public OllamaStreamingChatModelBuilder format(String format) {
            this.format = format;
            return this;
        }

        public OllamaStreamingChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public OllamaStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OllamaStreamingChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OllamaStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OllamaStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OllamaStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public OllamaStreamingChatModelBuilder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public OllamaStreamingChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            return supportedCapabilities(new HashSet<>(asList(supportedCapabilities)));
        }

        public OllamaStreamingChatModel build() {
            return new OllamaStreamingChatModel(
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
                    logRequests,
                    logResponses,
                    customHeaders,
                    listeners,
                    supportedCapabilities
            );
        }
    }
}
