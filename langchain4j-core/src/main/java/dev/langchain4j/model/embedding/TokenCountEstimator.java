package dev.langchain4j.model.embedding;

import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

public interface TokenCountEstimator {

    int estimateTokenCount(String text);

    default int estimateTokenCount(TextSegment textSegment) {
        return estimateTokenCount(textSegment.text());
    }

    int estimateTokenCount(List<TextSegment> textSegments);
}