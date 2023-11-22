package dev.langchain4j.model.vertexai.internal;

import dev.langchain4j.data.embedding.Embedding;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
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

        records.add(new VertexAiEmbeddingIndexRecord(id, embedding.vectorAsList()));

        if (embedding.dimension() > dimensions) {
            dimensions = embedding.dimension();
        }
    }

    @Override
    public String toString() {
        return records.stream()
                .map(VertexAiEmbeddingIndexRecord::toJson)
                .collect(Collectors.joining("\n"));
    }

    public static VertexAiEmbeddingIndex fromJson(String json) {
        final String[] lines = json.split("\n");
        final List<VertexAiEmbeddingIndexRecord> records = new ArrayList<>();
        int dimensions = 0;
        for (final String line : lines) {
            final VertexAiEmbeddingIndexRecord record = VertexAiEmbeddingIndexRecord.fromJson(line);
            records.add(record);
            if (record.getEmbedding().size() > dimensions) {
                dimensions = record.getEmbedding().size();
            }
        }

        return new VertexAiEmbeddingIndex(records, dimensions);
    }
}
