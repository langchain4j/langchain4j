package dev.langchain4j.model.chat.listener;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.model.chat.listener.ObservableChatModel.onRequest;

/**
 * // TODO reword
 * A decorator for {@link StreamingChatLanguageModel} that provides event notifications through {@link ChatModelListener}s
 * during the lifecycle of LLM API requests.
 * <p>
 * This interface enables monitoring and intercepting chat interactions by notifying
 * registered listeners at three key points:
 * <ul>
 *   <li>Before sending a request to the LLM API ({@link ChatModelListener#onRequest})
 *   <li>After receiving a successful response ({@link ChatModelListener#onResponse})
 *   <li>When an error occurs ({@link ChatModelListener#onError})
 * </ul>
 * <p>
 * Listeners can access and share data across these events using a shared attributes map that is passed to each
 * listener callback.
 * <p>
 * All listener callbacks are executed in a fail-safe manner - exceptions in listeners are logged but won't
 * disrupt the main request flow.
 *
 * @see ChatModelListener
 * @see ChatRequest
 * @see ChatResponse
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

        onRequest(finalChatRequest, attributes, listeners);
        doChat(finalChatRequest, observingHandler);
    }

    List<ChatModelListener> listeners();

    void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler);
}
