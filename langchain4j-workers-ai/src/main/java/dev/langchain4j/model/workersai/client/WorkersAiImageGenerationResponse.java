package dev.langchain4j.model.workersai.client;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;

/**
 * Response to generate an image.
 */
public class WorkersAiImageGenerationResponse
        extends ApiResponse<dev.langchain4j.model.workersai.client.WorkersAiImageGenerationResponse.ImageGenerationResult> {

    /**
     * Default constructor.
     */
    public WorkersAiImageGenerationResponse() {
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
