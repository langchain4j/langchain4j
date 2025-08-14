package dev.langchain4j.store.embedding;

import dev.langchain4j.model.output.TokenUsage;

/**
 * Represents the result of a {@link EmbeddingStoreIngestor} ingestion process.
 */
public class IngestionResult {

    /**
     * The token usage information.
     */
    private final TokenUsage tokenUsage;


    public IngestionResult(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }
}
