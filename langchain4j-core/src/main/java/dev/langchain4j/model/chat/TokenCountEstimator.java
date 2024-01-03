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

    /**
     * Estimates the count of tokens in the specified text.
     * @param text the text
     * @return the estimated count of tokens
     */
    default int estimateTokenCount(String text) {
        return estimateTokenCount(userMessage(text));
    }

    /**
     * Estimates the count of tokens in the specified message.
     * @param userMessage the message
     * @return the estimated count of tokens
     */
    default int estimateTokenCount(UserMessage userMessage) {
        return estimateTokenCount(singletonList(userMessage));
    }

    /**
     * Estimates the count of tokens in the specified prompt.
     * @param prompt the prompt
     * @return the estimated count of tokens
     */
    default int estimateTokenCount(Prompt prompt) {
        return estimateTokenCount(prompt.text());
    }

    /**
     * Estimates the count of tokens in the specified text segment.
     * @param textSegment the text segment
     * @return the estimated count of tokens
     */
    default int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }

    /**
     * Estimates the count of tokens in the specified list of messages.
     * @param messages the list of messages
     * @return the estimated count of tokens
     */
    int estimateTokenCount(List<ChatMessage> messages);
}
