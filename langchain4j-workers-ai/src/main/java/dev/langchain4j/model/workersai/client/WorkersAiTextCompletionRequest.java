package dev.langchain4j.model.workersai.client;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Request to complete a text.
 */
@Data @AllArgsConstructor
public class WorkersAiTextCompletionRequest {

    String prompt;

    /**
     * Default constructor.
     */
    public WorkersAiTextCompletionRequest() {
    }
}
