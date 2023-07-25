package dev.langchain4j.model.language;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;

public interface TokenCountEstimator {

    int estimateTokenCount(String text);

    default int estimateTokenCount(Prompt prompt) {
        return estimateTokenCount(prompt.text());
    }

    default int estimateTokenCount(Object structuredPrompt) {
        return estimateTokenCount(toPrompt(structuredPrompt));
    }

    default int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }
}
