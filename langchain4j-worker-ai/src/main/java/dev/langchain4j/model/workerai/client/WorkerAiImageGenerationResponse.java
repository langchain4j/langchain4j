package dev.langchain4j.model.workerai.client;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;

/**
 * Response to generate an image.
 */
public class WorkerAiImageGenerationResponse
        extends ApiResponse<WorkerAiImageGenerationResponse.ImageGenerationResult> {

    /**
     * Default constructor.
     */
    public WorkerAiImageGenerationResponse() {
    }

    /**
     * Body of the image generating process
     */
    @Data
    @AllArgsConstructor
    public static class ImageGenerationResult {
        private InputStream image;

        /**
         * Default constructor.
         */
        @SuppressWarnings("unused")
        public ImageGenerationResult() {
        }
    }

}
