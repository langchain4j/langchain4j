package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ListenersUtil;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a language model that has a chat API.
 *
 * @see StreamingChatLanguageModel
 */
public interface ChatLanguageModel {

    /**
     * This is the main API to interact with the chat model.
     * A temporary default implementation of this method is necessary
     * until all {@link ChatLanguageModel} implementations adopt it. It should be removed once that occurs.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @return a {@link ChatResponse}, containing all the outputs from the LLM
     */
    default ChatResponse chat(ChatRequest chatRequest) {

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        ListenersUtil.onRequest(finalChatRequest, attributes, listeners);
        try {
            ChatResponse chatResponse = doChat(finalChatRequest);
            ListenersUtil.onResponse(chatResponse, finalChatRequest, attributes, listeners);
            return chatResponse;
        } catch (Exception error) {
            ListenersUtil.onError(error, finalChatRequest, attributes, listeners);
            throw error;
        }
    }

    default ChatRequestParameters defaultRequestParameters() {
        return ChatRequestParameters.builder().build();
    }

    default List<ChatModelListener> listeners() {
        return Collections.emptyList();
    }

    default ChatResponse doChat(ChatRequest chatRequest) {
        throw new RuntimeException("Not implemented");
    }

    default String chat(String userMessage) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(userMessage))
                .build();

        ChatResponse chatResponse = chat(chatRequest);

        return chatResponse.aiMessage().text();
    }

    default ChatResponse chat(ChatMessage... messages) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

        return chat(chatRequest);
    }

    default ChatResponse chat(List<ChatMessage> messages) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

        return chat(chatRequest);
    }

    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    // TODO improve javadoc
}
