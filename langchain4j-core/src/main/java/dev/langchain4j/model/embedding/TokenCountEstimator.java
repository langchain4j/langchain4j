package dev.langchain4j.model.embedding;

import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

public interface TokenCountEstimator {

    int estimateTokenCount(String text);

    int estimateTokenCount(TextSegment textSegment);

    int estimateTokenCount(List<TextSegment> textSegments);
}