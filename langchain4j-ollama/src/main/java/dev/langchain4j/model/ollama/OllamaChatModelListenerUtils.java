package dev.langchain4j.model.ollama;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
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
     * @param listenerRequest The {@link dev.langchain4j.model.chat.request.ChatRequest} containing the request details.
     * @param attributes           A map of additional attributes to be passed to the context.
     */
    static void onListenRequest(
            List<ChatModelListener> listeners,
            dev.langchain4j.model.chat.request.ChatRequest listenerRequest,
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

    /**
     * Processes a listen response by notifying all registered chat model listeners.
     *
     * @param listeners            A list of {@link ChatModelListener} instances to be notified. Should not be null.
     * @param response             The {@link Response} containing the response details.
     * @param listenerRequest The original {@link dev.langchain4j.model.chat.request.ChatRequest} associated with the response.
     * @param attributes           A map of additional attributes to be passed to the context.
     */
    static void onListenResponse(List<ChatModelListener> listeners,
                                 Response<AiMessage> response,
                                 dev.langchain4j.model.chat.request.ChatRequest listenerRequest,
                                 ModelProvider modelProvider,
                                 Map<Object, Object> attributes) {
        dev.langchain4j.model.chat.response.ChatResponse listenerResponse =
                createListenerResponse(listenerRequest.parameters().modelName(), response);
        ChatModelResponseContext context = new ChatModelResponseContext(
                listenerResponse,
                listenerRequest,
                modelProvider,
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
     * @param listenerRequest The original {@link dev.langchain4j.model.chat.request.ChatRequest} associated with the response.
     * @param attributes           A map of additional attributes to be passed to the context.
     */
    static void onListenError(List<ChatModelListener> listeners,
                              Throwable error,
                              dev.langchain4j.model.chat.request.ChatRequest listenerRequest,
                              ModelProvider modelProvider,
                              Map<Object, Object> attributes) {
        ChatModelErrorContext context = new ChatModelErrorContext(
                error,
                listenerRequest,
                modelProvider,
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

    static dev.langchain4j.model.chat.request.ChatRequest createListenerRequest(
            ChatRequest request,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        Options options = request.getOptions();
        return dev.langchain4j.model.chat.request.ChatRequest.builder()
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

    static dev.langchain4j.model.chat.response.ChatResponse createListenerResponse(String responseModel,
                                                                                   Response<AiMessage> response) {
        if (response == null) {
            return null;
        }
        return dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .modelName(responseModel)
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }
}
