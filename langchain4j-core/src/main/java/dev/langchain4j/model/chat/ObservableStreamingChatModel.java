package dev.langchain4j.model.chat;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO
 */
public interface ObservableStreamingChatModel extends StreamingChatLanguageModel {

    @Override
    default void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        ObservableChatModel.onRequest(finalChatRequest, attributes, listeners);

        StreamingChatResponseHandler observingHandler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                ObservableChatModel.onResponse(completeResponse, finalChatRequest, attributes, listeners);
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                ObservableChatModel.onError(error, finalChatRequest, attributes, listeners);
                handler.onError(error);
            }
        };

        doChat(finalChatRequest, observingHandler);
    }

    List<ChatModelListener> listeners();

    void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler);
}
