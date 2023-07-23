package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;

import java.util.List;

public interface StreamingChatLanguageModel {

    void sendUserMessage(String userMessage, StreamingResponseHandler handler);

    void sendUserMessage(UserMessage userMessage, StreamingResponseHandler handler);

    void sendUserMessage(Object structuredPrompt, StreamingResponseHandler handler);

    void sendMessages(List<ChatMessage> messages, StreamingResponseHandler handler);

    void sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler handler);
}