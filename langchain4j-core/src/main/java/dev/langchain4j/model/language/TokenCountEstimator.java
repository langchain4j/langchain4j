package dev.langchain4j.model.language;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;

/**
 * Represents an interface for estimating the count of tokens in various text types such as a text, prompt, text segment, etc.
 * This can be useful when it's necessary to know in advance the cost of processing a specified text by the LLM.
 */
public interface TokenCountEstimator {

    /**
     * Estimates the count of tokens in the given text.
     * @param text the text.
     * @return the estimated count of tokens.
     */
    int estimateTokenCount(String text);

    /**
     * Estimates the count of tokens in the given prompt.
     * @param prompt the prompt.
     * @return the estimated count of tokens.
     */
    default int estimateTokenCount(Prompt prompt) {
        return estimateTokenCount(prompt.text());
    }

    /**
     * Estimates the count of tokens in the given text segment.
     *
     * <p>The metadata will not be included in the estimate.</p>
     *
     * @param textSegment the text segment.
     * @return the estimated count of tokens.
     */
    default int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }
}
