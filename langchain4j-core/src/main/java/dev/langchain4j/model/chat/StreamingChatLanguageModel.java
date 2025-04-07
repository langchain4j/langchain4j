package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ListenersUtil;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.model.ModelProvider.OTHER;

/**
 * Represents a language model that has a chat API and can stream a response one token at a time.
 *
 * @see ChatLanguageModel
 */
public interface StreamingChatLanguageModel {

    /**
     * This is the main API to interact with the chat model.
     * <p>
     * A temporary default implementation of this method is necessary
     * until all {@link StreamingChatLanguageModel} implementations adopt it. It should be removed once that occurs.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @param handler     a {@link StreamingChatResponseHandler} that will handle streaming response from the LLM
     */
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
                ListenersUtil.onResponse(completeResponse, finalChatRequest, provider(), attributes, listeners);
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                ListenersUtil.onError(error, finalChatRequest, provider(), attributes, listeners);
                handler.onError(error);
            }
        };

        ListenersUtil.onRequest(finalChatRequest, provider(), attributes, listeners);
        doChat(finalChatRequest, observingHandler);
    }

    default ChatRequestParameters defaultRequestParameters() {
        return ChatRequestParameters.builder().build();
    }

    default List<ChatModelListener> listeners() {
        return Collections.emptyList();
    }

    default ModelProvider provider() {
        return OTHER;
    }

    default void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        throw new RuntimeException("Not implemented");
    }

    default void chat(String userMessage, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(userMessage))
                .build();

        chat(chatRequest, handler);
    }

    default void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

        chat(chatRequest, handler);
    }

    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    // TODO improve javadoc
}
