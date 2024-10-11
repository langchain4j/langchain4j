package dev.langchain4j.store.embedding;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * Represents the result of a {@link EmbeddingStoreIngestor} ingest process.
 */
public class IngestionResult {
    /**
     * The embeddings content that were ingested.
     */
    private final List<Embedding> content;

    /**
     * The token usage information.
     */
    private final TokenUsage tokenUsage;

    /**
     * Additional metadata.
     */
    private final Map<String, Object> metadata;

    @Builder
    public IngestionResult(List<Embedding> content, TokenUsage tokenUsage, Map<String, Object> metadata) {
        this.content = ensureNotNull(content, "content");
        this.tokenUsage = ensureNotNull(tokenUsage, "tokenUsage");
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    public List<Embedding> content() {
        return content;
    }

    public TokenUsage tokenUsage() {
        return tokenUsage;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }
}
