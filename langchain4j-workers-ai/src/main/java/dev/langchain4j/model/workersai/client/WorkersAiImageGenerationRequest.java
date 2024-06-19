package dev.langchain4j.model.workersai.client;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Request to generate an image.
 */
@Data @AllArgsConstructor
public class WorkersAiImageGenerationRequest {

    /**
     * Prompt to generate the image.
     */
    String prompt;

    /**
     * Source image to edit
     */
    int[] image;

    /**
     * Mask image to edit (optional)
     */
    int[] mask;

    /**
     * Mask operation to apply.
     */
    Integer num_steps;

    /**
     * Strength
     */
    Integer strength;

    /**
     * File to save the image.
     */
    String destinationFile;

    /**
     * Default constructor.
     */
    public WorkersAiImageGenerationRequest() {
    }
}
