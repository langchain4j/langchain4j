package dev.langchain4j.model.workerai.client;

import lombok.Data;

import java.util.List;

/**
 * Response to compute embeddings
 */
public class WorkerAiEmbeddingResponse extends ApiResponse<WorkerAiEmbeddingResponse.EmbeddingResult>{

    /**
     * Default constructor.
     */
    public WorkerAiEmbeddingResponse() {
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
