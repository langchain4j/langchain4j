package dev.langchain4j.store.embedding;

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
     * The Vector storage id list
     */
    private final List<String> ids;


    public IngestionResult(TokenUsage tokenUsage, List<String> ids) {
        this.tokenUsage = tokenUsage;
        this.ids = ids;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public List<String> ids() {
        return ids;
    }
}
