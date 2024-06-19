package dev.langchain4j.model.workersai.client;

import lombok.Data;

import java.util.List;

/**
 * Response to compute embeddings
 */
public class WorkersAiEmbeddingResponse extends ApiResponse<WorkersAiEmbeddingResponse.EmbeddingResult>{

    /**
     * Default constructor.
     */
    public WorkersAiEmbeddingResponse() {
    }

    /**
     * Beam to hold results
     */
    @Data
    public static class EmbeddingResult {

        /**
         * Shape of the result
         */
        private List<Integer> shape;

        /**
         * Embedding data
         */
        private List<List<Float>> data;

        /**
         * Default constructor.
         */
        public EmbeddingResult() {
        }
    }

}
