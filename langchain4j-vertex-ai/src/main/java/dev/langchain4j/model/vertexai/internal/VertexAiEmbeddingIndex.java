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
     * @param embedding the embedding
     * @param metadata  the metadata
     * @return the id of the embedding
     */
    public String addEmbedding(Embedding embedding, Metadata metadata) {
        final String id = UUID.randomUUID().toString();

        records.add(new VertexAiEmbeddingIndexRecord(
                id,
                embedding.vectorAsList(),
                metadata != null
                        ? metadata.asMap()
                        : null));

        if (embedding.dimension() > dimensions) {
            dimensions = embedding.dimension();
        }

        return id;
    }

    /**
     * Adds an embedding to the index.
     *
     * @param embedding the embedding
     * @return the id of the embedding
     */
    public String addEmbedding(Embedding embedding) {
        return addEmbedding(embedding, null);
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
