package dev.langchain4j.store.embedding;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

/**
 * Represents the result of a {@link EmbeddingStoreIngestor} ingestion process.
 */
public class IngestionResult {

    /**
     * The token usage information.
     */
    private final TokenUsage tokenUsage;

    /**
     * The text segments that were ingested.
     */
    private List<TextSegment> textSegments;

    /**
     * The ids of the embeddings that were ingested.
     */
    private List<String> embeddingStoreIds;

    public IngestionResult(TokenUsage tokenUsage, List<TextSegment> textSegments, List<String> embeddingStoreIds) {
        this.tokenUsage = tokenUsage;
        this.textSegments = textSegments;
        this.embeddingStoreIds = embeddingStoreIds;
    }

    public IngestionResult(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public List<TextSegment> textSegments() {
        return textSegments;
    }

    public List<String> embeddingStoreIds() {
        return embeddingStoreIds;
    }
}
