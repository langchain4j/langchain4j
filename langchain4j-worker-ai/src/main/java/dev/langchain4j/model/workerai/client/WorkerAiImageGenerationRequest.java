package dev.langchain4j.model.workerai.client;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Request to generate an image.
 */
@Data @AllArgsConstructor
public class WorkerAiImageGenerationRequest  {

    /**
     * Prompt to generate the image.
     */
    String prompt;

    /**
     * File to save the image.
     */
    String destinationFile;

    /**
     * Default constructor.
     */
    public WorkerAiImageGenerationRequest() {
    }
}
