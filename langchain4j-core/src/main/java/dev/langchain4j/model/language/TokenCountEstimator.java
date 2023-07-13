package dev.langchain4j.model.language;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;

public interface TokenCountEstimator {

    int estimateTokenCount(String text);

    int estimateTokenCount(Prompt prompt);

    int estimateTokenCount(Object structuredPrompt);

    int estimateTokenCount(TextSegment textSegment);
}
