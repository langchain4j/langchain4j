package dev.langchain4j.model.workerai.client;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper for the text completion response.
 */
public class WorkerAiTextCompletionResponse extends ApiResponse<WorkerAiTextCompletionResponse.TextResponse> {

    /**
     * Default constructor.
     */
    public WorkerAiTextCompletionResponse() {}

    /**
     * Wrapper for the text completion response.
     */
    @Data
    public static class TextResponse {

        /**
         * The generated text.
         */
        private String response;

        /**
         * Default constructor.
         */
        public TextResponse() {}
    }
}
