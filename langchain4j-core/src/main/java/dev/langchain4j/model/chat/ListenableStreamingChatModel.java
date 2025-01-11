package dev.langchain4j.model.chat;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.model.chat.ListenableChatModel.onRequest;

/**
 * TODO
 */
public interface ListenableStreamingChatModel extends StreamingChatLanguageModel {

    @Override
    default void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        StreamingChatResponseHandler observingHandler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                ListenableChatModel.onResponse(completeResponse, finalChatRequest, attributes, listeners);
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                ListenableChatModel.onError(error, finalChatRequest, attributes, listeners);
                handler.onError(error);
            }
        };

        onRequest(finalChatRequest, attributes, listeners);
        doChat(finalChatRequest, observingHandler);
    }

    List<ChatModelListener> listeners();

    void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler);
}
