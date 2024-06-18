package dev.langchain4j.model.workersai.client;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Request to compute embeddings
 */
@Data
public class WorkersAiEmbeddingRequest {

    private List<String> text = new ArrayList<>();

    /**
     * Default constructor.
     */
    public WorkersAiEmbeddingRequest() {
    }
}
