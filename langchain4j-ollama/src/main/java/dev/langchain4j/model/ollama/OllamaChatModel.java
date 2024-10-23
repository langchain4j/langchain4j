package dev.langchain4j.model.ollama;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.ollama.spi.OllamaChatModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.createModelListenerRequest;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenError;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenRequest;
import static dev.langchain4j.model.ollama.OllamaChatModelListenerUtils.onListenResponse;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaMessages;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toOllamaTools;
import static dev.langchain4j.model.ollama.OllamaMessagesUtils.toToolExecutionRequest;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;

/**
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md">Ollama API reference</a>
 * <br>
 * <a href="https://github.com/jmorganca/ollama/blob/main/docs/modelfile.md#valid-parameters-and-values">Ollama API parameters</a>.
 */
public class OllamaChatModel implements ChatLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final String format;
    private final Integer maxRetries;
    private final List<ChatModelListener> listeners;

    public OllamaChatModel(String baseUrl,
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
                           Duration timeout,
                           Integer maxRetries,
                           Map<String, String> customHeaders,
                           Boolean logRequests,
                           Boolean logResponses,
                           List<ChatModelListener> listeners) {
        this.client = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout(getOrDefault(timeout, ofSeconds(60)))
                .customHeaders(customHeaders)
                .logRequests(getOrDefault(logRequests, false))
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
        this.format = format;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.listeners = new ArrayList<>(getOrDefault(listeners, emptyList()));
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

        return doGenerate(messages, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ensureNotEmpty(messages, "messages");

        return doGenerate(messages, toolSpecifications);
    }

    private Response<AiMessage> doGenerate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .messages(toOllamaMessages(messages))
                .options(options)
                .format(format)
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
                            AiMessage.from(toToolExecutionRequest(chatResponse.getMessage().getToolCalls())) :
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
        private Duration timeout;
        private Integer maxRetries;
        private Map<String, String> customHeaders;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;

        public OllamaChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
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

        public OllamaChatModelBuilder format(String format) {
            this.format = format;
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

        public OllamaChatModel build() {
            return new OllamaChatModel(
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
                    timeout,
                    maxRetries,
                    customHeaders,
                    logRequests,
                    logResponses,
                    listeners
            );
        }
    }
}
