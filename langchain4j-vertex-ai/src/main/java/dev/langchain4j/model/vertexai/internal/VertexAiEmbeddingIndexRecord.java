package dev.langchain4j.model.vertexai.internal;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

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
    private Map<String, String> metadata;

    public VertexAiEmbeddingIndexRecord(String id, List<Float> embedding, Map<String, String> metadata) {
        ensureNotBlank(id, "id cannot be blank");
        ensureNotNull(embedding, "embedding cannot be null");
        ensureNotEmpty(embedding, "embedding cannot be empty");

        this.id = id;
        this.embedding = embedding;
        this.metadata = metadata != null && !metadata.isEmpty()
                ? metadata
                : null;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
