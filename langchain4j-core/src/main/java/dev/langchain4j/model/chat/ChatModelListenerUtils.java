package dev.langchain4j.model.chat;

import dev.langchain4j.Internal;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Internal
class ChatModelListenerUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ChatModelListenerUtils.class);

    private ChatModelListenerUtils() {
    }

    static void onRequest(ChatRequest chatRequest,
                          ModelProvider modelProvider,
                          Map<Object, Object> attributes,
                          List<ChatModelListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        ChatModelRequestContext requestContext = new ChatModelRequestContext(chatRequest, modelProvider, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                LOG.warn("An exception occurred during the invocation of the chat model listener. " +
                        "This exception has been ignored.", e);
            }
        });
    }

    static void onResponse(ChatResponse chatResponse,
                           ChatRequest chatRequest,
                           ModelProvider modelProvider,
                           Map<Object, Object> attributes,
                           List<ChatModelListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                chatResponse, chatRequest, modelProvider, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onResponse(responseContext);
            } catch (Exception e) {
                LOG.warn("An exception occurred during the invocation of the chat model listener. " +
                        "This exception has been ignored.", e);
            }
        });
    }

    static void onError(Throwable error,
                        ChatRequest chatRequest,
                        ModelProvider modelProvider,
                        Map<Object, Object> attributes,
                        List<ChatModelListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        ChatModelErrorContext errorContext = new ChatModelErrorContext(error, chatRequest, modelProvider, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onError(errorContext);
            } catch (Exception e) {
                LOG.warn("An exception occurred during the invocation of the chat model listener. " +
                        "This exception has been ignored.", e);
            }
        });
    }
}
