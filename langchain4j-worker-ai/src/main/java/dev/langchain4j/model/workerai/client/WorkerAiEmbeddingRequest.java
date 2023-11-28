package dev.langchain4j.model.workerai.client;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Request to compute embeddings
 */
@Data
public class WorkerAiEmbeddingRequest {

    private List<String> text = new ArrayList<>();

    /**
     * Default constructor.
     */
    public WorkerAiEmbeddingRequest() {
    }
}
