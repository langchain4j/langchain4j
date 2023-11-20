package dev.langchain4j.model.vertexai.internal;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.*;

/**
 * Represents an embedding index.
 */
@Getter
@Setter
@AllArgsConstructor
public class VertexAiEmbeddingIndex {
    private final List<VertexAiEmbeddingIndexRecord> records;
    private int dimensions;

    public VertexAiEmbeddingIndex() {
        this.records = new ArrayList<>();
        this.dimensions = 0;
    }

    public int size() {
        return records.size();
    }

    /**
     * Adds an embedding to the index.
     *
     * @param id        the id of the embedding
     * @param embedding the embedding
     * @param metadata  the metadata
     */
    public void addEmbedding(String id, Embedding embedding, Metadata metadata) {
        records.add(new VertexAiEmbeddingIndexRecord(
                id,
                embedding.vectorAsList(),
                metadata != null
                        ? metadata.asMap()
                        : null));

        if (embedding.dimension() > dimensions) {
            dimensions = embedding.dimension();
        }
    }

    /**
     * Adds an embedding to the index.
     *
     * @param id        the id of the embedding
     * @param embedding the embedding
     */
    public void addEmbedding(String id, Embedding embedding) {
        ensureNotBlank(id, "id cannot be blank");
        ensureNotNull(embedding, "embedding cannot be null");
        ensureTrue(embedding.dimension() > 0, "embedding must have at least one dimension");

        addEmbedding(id, embedding, null);
    }

    /**
     * Creates an embedding index from a list of embeddings.
     *
     * @param embeddings the embeddings
     * @return the index
     */
    public static VertexAiEmbeddingIndex fromEmbeddings(List<Embedding> embeddings) {
        int dimensions = 0;

        final List<VertexAiEmbeddingIndexRecord> records = new ArrayList<>();
        for (final Embedding embedding : embeddings) {
            records.add(new VertexAiEmbeddingIndexRecord(
                    UUID.randomUUID().toString(),
                    embedding.vectorAsList(),
                    null));

            if (embedding.dimension() > dimensions) {
                dimensions = embedding.dimension();
            }
        }

        return new VertexAiEmbeddingIndex(records, dimensions);
    }

    @Override
    public String toString() {
        return records.stream()
                .map(VertexAiEmbeddingIndexRecord::toJson)
                .collect(Collectors.joining("\n"));
    }
}
