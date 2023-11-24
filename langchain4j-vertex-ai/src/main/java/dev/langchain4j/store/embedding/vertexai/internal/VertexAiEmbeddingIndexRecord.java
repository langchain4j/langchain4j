package dev.langchain4j.store.embedding.vertexai.internal;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.*;

/**
 * This class is used to serialize the embedding index record to JSON.
 */
@Getter
@Setter
public class VertexAiEmbeddingIndexRecord {
    private static final Gson GSON = new Gson();

    private String id;
    private List<Float> embedding;

    public VertexAiEmbeddingIndexRecord(String id, List<Float> embedding) {
        ensureNotBlank(id, "id");
        ensureNotNull(embedding, "embedding");
        ensureNotEmpty(embedding, "embedding");

        this.id = id;
        this.embedding = embedding;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static VertexAiEmbeddingIndexRecord fromJson(String json) {
        return GSON.fromJson(json, VertexAiEmbeddingIndexRecord.class);
    }
}
