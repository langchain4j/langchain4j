package dev.langchain4j.model.workersai.client;

import lombok.Data;

/**
 * Wrapper for the text completion response.
 */
public class WorkersAiTextCompletionResponse extends ApiResponse<dev.langchain4j.model.workersai.client.WorkersAiTextCompletionResponse.TextResponse> {

    /**
     * Default constructor.
     */
    public WorkersAiTextCompletionResponse() {}

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
