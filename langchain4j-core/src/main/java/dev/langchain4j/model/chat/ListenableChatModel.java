package dev.langchain4j.model.chat;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO
 */
public interface ListenableChatModel extends ChatLanguageModel {

    Logger log = LoggerFactory.getLogger(ListenableChatModel.class);

    @Override
    default ChatResponse chat(ChatRequest chatRequest) {

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        onRequest(finalChatRequest, attributes, listeners);
        try {
            ChatResponse chatResponse = doChat(finalChatRequest);
            onResponse(chatResponse, finalChatRequest, attributes, listeners);
            return chatResponse;
        } catch (Exception error) {
            onError(error, finalChatRequest, attributes, listeners);
            throw error;
        }
    }

    List<ChatModelListener> listeners();

    ChatResponse doChat(ChatRequest chatRequest);

    static void onRequest(ChatRequest chatRequest,
                          Map<Object, Object> attributes,
                          List<ChatModelListener> listeners) {

        ChatModelRequestContext requestContext = new ChatModelRequestContext(chatRequest, attributes);

        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("An exception occurred during the invocation of the chat model listener", e);
            }
        });
    }

    static void onResponse(ChatResponse chatResponse,
                           ChatRequest chatRequest,
                           Map<Object, Object> attributes,
                           List<ChatModelListener> listeners) {

        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                chatResponse,
                chatRequest,
                attributes
        );

        listeners.forEach(listener -> {
            try {
                listener.onResponse(responseContext);
            } catch (Exception e) {
                log.warn("An exception occurred during the invocation of the chat model listener", e);
            }
        });
    }

    static void onError(Throwable error,
                        ChatRequest chatRequest,
                        Map<Object, Object> attributes,
                        List<ChatModelListener> listeners) {

        ChatModelErrorContext errorContext = new ChatModelErrorContext(
                error,
                chatRequest,
                null,
                attributes
        );

        listeners.forEach(listener -> {
            try {
                listener.onError(errorContext);
            } catch (Exception e) {
                log.warn("An exception occurred during the invocation of the chat model listener", e);
            }
        });
    }
}
