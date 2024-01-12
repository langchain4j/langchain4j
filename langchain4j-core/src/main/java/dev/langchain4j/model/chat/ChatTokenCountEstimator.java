package dev.langchain4j.model.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.language.TokenCountEstimator;

import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;

/**
 * Represents an interface for estimating the count of tokens in various text types such as a text, message, prompt, text segment, etc.
 * This can be useful when it's necessary to know in advance the cost of processing a specified text by the LLM.
 */
public interface ChatTokenCountEstimator extends TokenCountEstimator {

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
        return estimateChatMessagesTokenCount(singletonList(userMessage));
    }

    /**
     * Estimates the count of tokens in the specified list of messages.
     * @param messages the list of messages
     * @return the estimated count of tokens
     */
    int estimateChatMessagesTokenCount(List<ChatMessage> messages);
}
