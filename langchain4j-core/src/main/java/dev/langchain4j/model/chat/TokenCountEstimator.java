package dev.langchain4j.model.chat;

import dev.langchain4j.MightChangeInTheFuture;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.Prompt;

import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static java.util.Collections.singletonList;

public interface TokenCountEstimator {

    default int estimateTokenCount(String text) {
        return estimateTokenCount(userMessage(text));
    }

    default int estimateTokenCount(UserMessage userMessage) {
        return estimateTokenCount(singletonList(userMessage));
    }

    @MightChangeInTheFuture("not sure this method is useful/needed")
    default int estimateTokenCount(Prompt prompt) {
        return estimateTokenCount(prompt.text());
    }

    @MightChangeInTheFuture("not sure this method is useful/needed")
    default int estimateTokenCount(Object structuredPrompt) {
        return estimateTokenCount(toPrompt(structuredPrompt));
    }

    int estimateTokenCount(List<ChatMessage> messages);

    default int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }
}
