package dev.langchain4j.model.embedding;

import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * Represents an interface for estimating the count of tokens in various texts, text segments, etc.
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

    /**
     * Estimates the count of tokens in the given text segments.
     *
     * <p>The metadata will not be included in the estimate.</p>
     *
     * @param textSegments the text segments.
     * @return the estimated count of tokens.
     */
    default int estimateTokenCount(List<TextSegment> textSegments) {
        int tokenCount = 0;
        for (TextSegment textSegment : textSegments) {
            tokenCount += estimateTokenCount(textSegment);
        }
        return tokenCount;
    }
}