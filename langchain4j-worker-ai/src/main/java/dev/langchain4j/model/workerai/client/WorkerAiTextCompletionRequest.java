package dev.langchain4j.model.workerai.client;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Request to complete a text.
 */
@Data @AllArgsConstructor
public class WorkerAiTextCompletionRequest {

    String prompt;

    /**
     * Default constructor.
     */
    public WorkerAiTextCompletionRequest() {
    }
}
