package dev.langchain4j.model.ollama;

import static dev.langchain4j.model.ollama.InternalOllamaHelper.toOllamaMessages;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.toOllamaResponseFormat;
import static dev.langchain4j.model.ollama.InternalOllamaHelper.toOllamaTools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OllamaChatModelListenerUtils {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatModelListenerUtils.class);

    private OllamaChatModelListenerUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    /**
     * Processes a listen request by notifying all registered chat model listeners.
     *
     * @param listeners       A list of {@link ChatModelListener} instances to be notified. Should not be null.
     * @param listenerRequest The {@link ChatRequest} containing the request details.
     * @param attributes      A map of additional attributes to be passed to the context.
     */
    static void onListenRequest(
            List<ChatModelListener> listeners,
            ChatRequest listenerRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        ChatModelRequestContext context = new ChatModelRequestContext(listenerRequest, modelProvider, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
    }

    static OllamaChatRequest toOllamaChatRequest(ChatRequest chatRequest) {
        OllamaChatRequestParameters requestParameters = (OllamaChatRequestParameters) chatRequest.parameters();
        return OllamaChatRequest.builder()
                .model(requestParameters.modelName())
                .messages(toOllamaMessages(chatRequest.messages()))
                .options(Options.builder()
                        .mirostat(requestParameters.mirostat())
                        .mirostatEta(requestParameters.mirostatEta())
                        .mirostatTau(requestParameters.mirostatTau())
                        .repeatLastN(requestParameters.repeatLastN())
                        .temperature(requestParameters.temperature())
                        .topK(requestParameters.topK())
                        .topP(requestParameters.topP())
                        .repeatPenalty(requestParameters.repeatPenalty())
                        .seed(requestParameters.seed())
                        .numPredict(requestParameters.numPredict())
                        .numCtx(requestParameters.numCtx())
                        .stop(requestParameters.stopSequences())
                        .minP(requestParameters.minP())
                        .build())
                .format(toOllamaResponseFormat(requestParameters.responseFormat()))
                .stream(false)
                .tools(toOllamaTools(chatRequest.toolSpecifications()))
                .build();
    }

    /**
     * Processes a listen response by notifying all registered chat model listeners.
     *
     * @param listeners       A list of {@link ChatModelListener} instances to be notified. Should not be null.
     * @param response        The {@link Response} containing the response details.
     * @param listenerRequest The original {@link ChatRequest} associated with the response.
     * @param attributes      A map of additional attributes to be passed to the context.
     */
    static void onListenResponse(
            List<ChatModelListener> listeners,
            ChatResponse response,
            ChatRequest listenerRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        ChatModelResponseContext context =
                new ChatModelResponseContext(response, listenerRequest, modelProvider, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onResponse(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
    }

    /**
     * Processes a listen error by notifying all registered chat model listeners.
     *
     * @param listeners       A list of {@link ChatModelListener} instances to be notified. Should not be null.
     * @param error           Error between chat
     * @param listenerRequest The original {@link ChatRequest} associated with the response.
     * @param attributes      A map of additional attributes to be passed to the context.
     */
    static void onListenError(
            List<ChatModelListener> listeners,
            Throwable error,
            ChatRequest listenerRequest,
            ModelProvider modelProvider,
            Map<Object, Object> attributes) {
        ChatModelErrorContext context = new ChatModelErrorContext(error, listenerRequest, modelProvider, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onError(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
    }

    static ChatRequest createListenerRequest(
            OllamaChatRequest request, List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        Options options = request.getOptions();
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(request.getModel())
                        .temperature(options.getTemperature())
                        .topP(options.getTopP())
                        .maxOutputTokens(options.getNumPredict())
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }
}
