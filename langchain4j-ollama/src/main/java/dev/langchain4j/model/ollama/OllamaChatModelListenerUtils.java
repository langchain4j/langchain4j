package dev.langchain4j.model.ollama;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

class OllamaChatModelListenerUtils {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatModelListenerUtils.class);

    private OllamaChatModelListenerUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    /**
     * Processes a listen request by notifying all registered chat model listeners.
     *
     * @param listeners            A list of {@link ChatModelListener} instances to be notified. Should not be null.
     * @param modelListenerRequest The {@link ChatModelRequest} containing the request details.
     * @param attributes           A map of additional attributes to be passed to the context.
     */
    static void onListenRequest(List<ChatModelListener> listeners, ChatModelRequest modelListenerRequest, Map<Object, Object> attributes) {
        ChatModelRequestContext context = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
    }

    /**
     * Processes a listen response by notifying all registered chat model listeners.
     *
     * @param listeners            A list of {@link ChatModelListener} instances to be notified. Should not be null.
     * @param response             The {@link Response} containing the response details.
     * @param modelListenerRequest The original {@link ChatModelRequest} associated with the response.
     * @param attributes           A map of additional attributes to be passed to the context.
     */
    static void onListenResponse(List<ChatModelListener> listeners, Response<AiMessage> response, ChatModelRequest modelListenerRequest, Map<Object, Object> attributes) {
        ChatModelResponse modelListenerResponse = createModelListenerResponse(modelListenerRequest.model(), response);
        ChatModelResponseContext context = new ChatModelResponseContext(
                modelListenerResponse,
                modelListenerRequest,
                attributes
        );
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
     * @param listeners            A list of {@link ChatModelListener} instances to be notified. Should not be null.
     * @param error                Error between chat
     * @param modelListenerRequest The original {@link ChatModelRequest} associated with the response.
     * @param partialResponse      The partial {@link Response} containing cur response details.
     * @param attributes           A map of additional attributes to be passed to the context.
     */
    static void onListenError(List<ChatModelListener> listeners, Throwable error, ChatModelRequest modelListenerRequest, Response<AiMessage> partialResponse, Map<Object, Object> attributes) {
        ChatModelResponse partialModelListenerResponse = createModelListenerResponse(modelListenerRequest.model(), partialResponse);
        ChatModelErrorContext context = new ChatModelErrorContext(
                error,
                modelListenerRequest,
                partialModelListenerResponse,
                attributes
        );
        listeners.forEach(listener -> {
            try {
                listener.onError(context);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
    }

    static ChatModelRequest createModelListenerRequest(ChatRequest request,
                                                       List<ChatMessage> messages,
                                                       List<ToolSpecification> toolSpecifications) {
        Options options = request.getOptions();

        return ChatModelRequest.builder()
                .model(request.getModel())
                .temperature(options.getTemperature())
                .topP(options.getTopP())
                .maxTokens(options.getNumPredict())
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
    }

    static ChatModelResponse createModelListenerResponse(String responseModel,
                                                         Response<AiMessage> response) {
        if (response == null) {
            return null;
        }

        return ChatModelResponse.builder()
                .model(responseModel)
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .aiMessage(response.content())
                .build();
    }
}
