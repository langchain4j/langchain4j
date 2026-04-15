package dev.langchain4j.model.chat;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.ModelProvider.OTHER;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onError;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onRequest;
import static dev.langchain4j.model.chat.ChatModelListenerUtils.onResponse;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a language model that has a chat API.
 *
 * @see StreamingChatModel
 */
public interface ChatModel {

    /**
     * This is the main API to interact with the chat model.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @return a {@link ChatResponse}, containing all the outputs from the LLM
     */
    default ChatResponse chat(ChatRequest chatRequest) {
        return chat(chatRequest, ChatRequestOptions.EMPTY);
    }

    /**
     * Sends a chat request with additional invocation options.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @param options     a {@link ChatRequestOptions} carrying listener attributes and other per-call metadata
     * @return a {@link ChatResponse}, containing all the outputs from the LLM
     * @since 1.13.0
     */
    default ChatResponse chat(ChatRequest chatRequest, ChatRequestOptions options) {

        ChatRequestOptions effectiveOptions = getOrDefault(options, ChatRequestOptions.EMPTY);

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>(effectiveOptions.listenerAttributes());

        onRequest(finalChatRequest, provider(), attributes, listeners);
        try {
            ChatResponse chatResponse = doChat(finalChatRequest);
            onResponse(chatResponse, finalChatRequest, provider(), attributes, listeners);
            return chatResponse;
        } catch (Exception error) {
            onError(error, finalChatRequest, provider(), attributes, listeners);
            throw error;
        }
    }

    default ChatResponse doChat(ChatRequest chatRequest) {
        throw new RuntimeException("Not implemented");
    }

    default ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.EMPTY;
    }

    default List<ChatModelListener> listeners() {
        return List.of();
    }

    default ModelProvider provider() {
        return OTHER;
    }

    default String chat(String userMessage) {

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from(userMessage)).build();

        ChatResponse chatResponse = chat(chatRequest);

        return chatResponse.aiMessage().text();
    }

    default ChatResponse chat(ChatMessage... messages) {

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

        return chat(chatRequest);
    }

    default ChatResponse chat(List<ChatMessage> messages) {

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();

        return chat(chatRequest);
    }

    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }
}
