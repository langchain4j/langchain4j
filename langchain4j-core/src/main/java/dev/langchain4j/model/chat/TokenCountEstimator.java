package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;

import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;

/**
 * Represents an interface for estimating the count of tokens in various text types such as a text, message, prompt, text segment, etc.
 * This can be useful when it's necessary to know in advance the cost of processing a specified text by the LLM.
 */
public interface TokenCountEstimator {

    default int estimateTokenCount(String text) {
        return estimateTokenCount(userMessage(text));
    }

    default int estimateTokenCount(UserMessage userMessage) {
        return estimateTokenCount(singletonList(userMessage));
    }

    default int estimateTokenCount(Prompt prompt) {
        return estimateTokenCount(prompt.text());
    }

    default int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }

    int estimateTokenCount(List<ChatMessage> messages);
}
