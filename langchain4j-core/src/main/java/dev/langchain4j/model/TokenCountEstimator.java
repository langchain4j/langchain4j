package dev.langchain4j.model;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Represents an interface for estimating the count of tokens in various text types such as a text, prompt, text segment, etc.
 * This can be useful when it's necessary to know in advance the cost of processing a specified text by the LLM.
 */
public interface TokenCountEstimator {

    /**
     * Estimates the count of tokens in the given text.
     *
     * @param text the text.
     * @return the estimated count of tokens.
     */
    int estimateTokenCountInText(String text);

    /**
     * Estimates the count of tokens in the given message.
     *
     * @param message the message.
     * @return the estimated count of tokens.
     */
    int estimateTokenCountInMessage(ChatMessage message);

    /**
     * Estimates the count of tokens in the given messages.
     *
     * @param messages the messages.
     * @return the estimated count of tokens.
     */
    int estimateTokenCountInMessages(Iterable<ChatMessage> messages);
}
