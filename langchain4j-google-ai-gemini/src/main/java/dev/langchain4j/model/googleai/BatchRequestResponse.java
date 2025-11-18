package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingRequest;
import java.util.List;
import java.util.Map;

final class BatchRequestResponse {
    private BatchRequestResponse() {}

    /**
     * Represents a batch request for generating content using the Gemini API.
     * A batch allows processing multiple {@link GeminiGenerateContentRequest} objects asynchronously.
     */
    record BatchGenerateContentRequest(Batch batch) {

        /**
         * The batch configuration containing display name, input config, and priority.
         *
         * @param displayName Required. The user-defined name of this batch.
         * @param inputConfig Configuration for the input to the batch request.
         * @param priority    Optional. The priority of the batch. Batches with a higher priority value will be processed before
         *                    batches with a lower priority value. Negative values are allowed. Default is 0.
         */
        record Batch(
                @JsonProperty("display_name") String displayName,
                @JsonProperty("input_config") InputConfig inputConfig,
                long priority) {}

        /**
         * Configures the input to the batch request.
         *
         * @param requests The list of inlined requests to be processed in the batch.
         */
        record InputConfig(Requests requests) {}

        /**
         * Wrapper for the list of inlined requests.
         *
         * @param requests The list of inlined requests to be processed in the batch.
         */
        record Requests(List<InlinedRequest> requests) {}

        /**
         * Individual request to be processed in the batch.
         *
         * @param request  Required. The {@link GeminiGenerateContentRequest} to be processed in the batch.
         * @param metadata Optional. The metadata to be associated with the request.
         */
        record InlinedRequest(Object request, Map<String, String> metadata) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchGenerateContentResponse(
            String name, Map<String, Object> metadata, boolean done, Response response) {

        /**
         * The batch output containing the inlined responses.
         *
         * @param type             The type identifier for the response.
         * @param inlinedResponses The wrapper object containing the list of inlined responses.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Response(@JsonProperty("@type") String type, InlinedResponses inlinedResponses) {}

        /**
         * Wrapper for the list of inlined responses.
         *
         * @param inlinedResponses The list of individual response wrappers.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record InlinedResponses(List<InlinedResponseWrapper> inlinedResponses) {}

        /**
         * Wrapper for an individual response.
         *
         * @param response The actual Gemini response.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record InlinedResponseWrapper(GeminiGenerateContentResponse response) {}
    }

    /**
     * Represents a long-running operation that is the result of a network API call.
     *
     * @param name     The server-assigned name, which is only unique within the same service that originally returns it.
     *                 If you use the default HTTP mapping, the name should be a resource name ending with {@code operations/{unique_id}}.
     * @param metadata Service-specific metadata associated with the operation. It typically contains progress information
     *                 and common metadata such as create time. Some services might not provide such metadata.
     *                 An object containing fields of an arbitrary type. An additional field {@code "@type"} contains a URI
     *                 identifying the type.
     * @param done     If the value is {@code false}, it means the operation is still in progress. If {@code true}, the operation
     *                 is completed, and either {@code error} or {@code response} is available.
     * @param error    The error result of the operation in case of failure or cancellation. Only present when {@code done} is
     *                 {@code true} and the operation failed.
     * @param response The normal, successful response of the operation. If the original method returns no data on success,
     *                 such as Delete, the response is {@code google.protobuf.Empty}. If the original method is standard
     *                 Get/Create/Update, the response should be the resource. For other methods, the response should have
     *                 the type {@code XxxResponse}, where {@code Xxx} is the original method name. Only present when
     *                 {@code done} is {@code true} and the operation succeeded.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operation(
            String name,
            Map<String, Object> metadata,
            boolean done,
            Status error,
            BatchGenerateContentResponse.Response response) {

        /**
         * Represents the error status of an operation.
         *
         * @param code    The status code.
         * @param message A developer-facing error message.
         * @param details A list of messages that carry the error details.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Status(int code, String message, List<Map<String, Object>> details) {}
    }
}
