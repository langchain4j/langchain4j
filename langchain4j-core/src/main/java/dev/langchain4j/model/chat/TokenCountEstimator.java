package dev.langchain4j.model.chat;

import dev.langchain4j.MightChangeInTheFuture;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.Prompt;

import java.util.List;

public interface TokenCountEstimator {

    int estimateTokenCount(String text);

    int estimateTokenCount(UserMessage userMessage);

    @MightChangeInTheFuture("not sure this method is useful/needed")
    int estimateTokenCount(Prompt prompt);

    @MightChangeInTheFuture("not sure this method is useful/needed")
    int estimateTokenCount(Object structuredPrompt);

    int estimateTokenCount(List<ChatMessage> messages);

    int estimateTokenCount(TextSegment textSegment);
}
