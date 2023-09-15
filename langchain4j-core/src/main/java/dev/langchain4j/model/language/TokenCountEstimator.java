package dev.langchain4j.model.language;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;

/**
 * Represents an interface for estimating the count of tokens in various text types such as a text, prompt, text segment, etc.
 * This can be useful when it's necessary to know in advance the cost of processing a specified text by the LLM.
 */
public interface TokenCountEstimator {

    int estimateTokenCount(String text);

    default int estimateTokenCount(Prompt prompt) {
        return estimateTokenCount(prompt.text());
    }

    default int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }
}
