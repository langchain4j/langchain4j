package dev.langchain4j.model.vertexai.internal;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

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
