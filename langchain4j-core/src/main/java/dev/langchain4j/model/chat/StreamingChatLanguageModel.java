package dev.langchain4j.model.chat;

import dev.langchain4j.WillChangeSoon;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResultHandler;
import dev.langchain4j.model.input.Prompt;

import java.util.List;

public interface StreamingChatLanguageModel {

    @WillChangeSoon("Most probably StreamingResultHandler will be replaced with fluent API")
    void sendUserMessage(String userMessage, StreamingResultHandler handler);

    @WillChangeSoon("Most probably StreamingResultHandler will be replaced with fluent API")
    void sendUserMessage(UserMessage userMessage, StreamingResultHandler handler);

    @WillChangeSoon("Most probably StreamingResultHandler will be replaced with fluent API")
    void sendUserMessage(Object structuredPrompt, StreamingResultHandler handler);

    @WillChangeSoon("Most probably StreamingResultHandler will be replaced with fluent API")
    void sendMessages(List<ChatMessage> messages, StreamingResultHandler handler);
}