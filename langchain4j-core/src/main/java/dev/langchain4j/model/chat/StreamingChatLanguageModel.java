package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;

import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;

/**
 * Represents a language model that has a chat interface and can stream a response one token at a time.
 */
public interface StreamingChatLanguageModel {

    default void generate(String userMessage, StreamingResponseHandler handler) {
        generate(userMessage(userMessage), handler);
    }

    default void generate(UserMessage userMessage, StreamingResponseHandler handler) {
        generate(singletonList(userMessage), handler);
    }

    void generate(List<ChatMessage> messages, StreamingResponseHandler handler);

    void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler handler);

    void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler handler);
}