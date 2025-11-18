package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

final class BatchRequestResponse {
    private BatchRequestResponse() {}

    /**
     * Represents a batch request for the Gemini API.
     * A batch allows processing multiple requests asynchronously.
     *
     * @param <REQ> The type of request (e.g., GeminiGenerateContentRequest, GeminiEmbeddingRequest)
     */
    record BatchCreateRequest<REQ>(Batch<REQ> batch) {

        /**
         * The batch configuration containing display name, input config, and priority.
         */
        record Batch<REQ>(
                @JsonProperty("display_name") String displayName,
                @JsonProperty("input_config") InputConfig<REQ> inputConfig,
                long priority) {}

        /**
         * Configures the input to the batch request.
         */
        record InputConfig<REQ>(Requests<REQ> requests) {}

        /**
         * Wrapper for the list of inlined requests.
         */
        record Requests<REQ>(List<InlinedRequest<REQ>> requests) {}

        /**
         * Individual request to be processed in the batch.
         *
         * @param request  Required. The request to be processed in the batch.
         * @param metadata Optional. The metadata to be associated with the request.
         */
        record InlinedRequest<REQ>(REQ request, Map<String, String> metadata) {}
    }

    /**
     * Represents the response from a batch operation.
     *
     * @param <RESP> The type of response (e.g., GeminiGenerateContentResponse, GeminiEmbeddingResponse)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchCreateResponse<RESP>(
            @JsonProperty("@type") String type, InlinedResponses<RESP> inlinedResponses) {

        /**
         * Wrapper for the list of inlined responses.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record InlinedResponses<RESP>(List<InlinedResponseWrapper<RESP>> inlinedResponses) {}

        /**
         * Wrapper for an individual response.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record InlinedResponseWrapper<RESP>(RESP response) {}
    }

    /**
     * Represents a long-running operation that is the result of a network API call.
     *
     * @param <RESP> The type of response in the operation result
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operation<RESP>(
            String name, Map<String, Object> metadata, boolean done, Status error, BatchCreateResponse<RESP> response) {

        /**
         * Represents the error status of an operation.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Status(int code, String message, List<Map<String, Object>> details) {}
    }
}
