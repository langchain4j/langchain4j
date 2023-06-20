package dev.langchain4j.model.embedding;

import dev.langchain4j.data.document.DocumentSegment;

import java.util.List;

public interface TokenCountEstimator {

    int estimateTokenCount(String text);

    int estimateTokenCount(DocumentSegment documentSegment);

    int estimateTokenCount(List<DocumentSegment> documentSegments);
}