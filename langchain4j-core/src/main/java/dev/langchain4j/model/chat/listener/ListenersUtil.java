package dev.langchain4j.model.chat.listener;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class ListenersUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ListenersUtil.class);

    private ListenersUtil() {
    }

    public static void onRequest(ChatRequest chatRequest,
                                 String system,
                                 Map<Object, Object> attributes,
                                 List<ChatModelListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        ChatModelRequestContext requestContext = new ChatModelRequestContext(chatRequest, system, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                LOG.warn("An exception occurred during the invocation of the chat model listener. " +
                        "This exception has been ignored.", e);
            }
        });
    }

    public static void onResponse(ChatResponse chatResponse,
                                  ChatRequest chatRequest,
                                  String system,
                                  Map<Object, Object> attributes,
                                  List<ChatModelListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        ChatModelResponseContext responseContext = new ChatModelResponseContext(chatResponse, chatRequest, system, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onResponse(responseContext);
            } catch (Exception e) {
                LOG.warn("An exception occurred during the invocation of the chat model listener. " +
                        "This exception has been ignored.", e);
            }
        });
    }

    public static void onError(Throwable error,
                               ChatRequest chatRequest,
                               String system,
                               Map<Object, Object> attributes,
                               List<ChatModelListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        ChatModelErrorContext errorContext = new ChatModelErrorContext(error, chatRequest, system, attributes);
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
