package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.googleai.GoogleAiGeminiBatchChatModel.BatchGenerateContentRequest.*;

/**
 * The {@code GoogleAiGeminiBatchChatModel} class provides an interface for interacting with the Gemini Batch API,
 * an asynchronous service designed for processing large volumes of requests at a reduced cost (50% of standard).
 * It is ideal for non-urgent, large-scale tasks like data pre-processing or evaluations, with a Service Level
 * Objective (SLO) of 24-hour turnaround, though completion is often much quicker.
 */
@Experimental
public final class GoogleAiGeminiBatchChatModel extends BaseGeminiChatModel {
    GoogleAiGeminiBatchChatModel(final Builder builder) {
        this(builder, buildGeminiService(builder));
    }

    GoogleAiGeminiBatchChatModel(final Builder builder, final GeminiService geminiService) {
        super(builder, geminiService);
    }

    public Operation createBatchInline(String displayName, @Nullable Long priority, List<ChatRequest> requests) {
        var modelName = extractModelFromRequests(requests);
        var inlineRequests = requests.stream()
                // Merge the chat requests with the values set in the Builder.
                .map(this::applyDefaultParameters)
                // Create a Gemini specific content requests for each ChatRequest.
                .map(this::createGenerateContentRequest)
                // Wrap them in InlinedRequest, ready for batching.
                .map(request -> new InlinedRequest(request, Map.of()))
                .toList();
        var request = new BatchGenerateContentRequest(new Batch(
                displayName,
                new InputConfig(new Requests(inlineRequests)),
                getOrDefault(priority, 0L)));

        return geminiService.batchGenerateContent(modelName, request);
    }

    public BatchJobState getBatchJobStatus(String operationName) {
        // Implementation for getting job status
        return BatchJobState.JOB_STATE_PENDING;
    }

    public Object retrieveBatchResults(String operationName) {
        // Implementation for retrieving batch job results
        // This might return a List<GenerateContentResponse> or a String representing a file URI
        return null;
    }

    public void cancelBatchJob(String operationName) {
        // Implementation for cancelling a batch job
    }

    /**
     * Deletes an existing batch job - when a job is deleted, it stops processing new requests and is permanently
     * removed from the list of batch jobs.
     *
     * @param operationName The unique operation name (ID) of the batch job to delete.
     */
    public void deleteBatchJob(String operationName) {
        // Implementation for deleting a batch job
    }

    private ChatRequest applyDefaultParameters(ChatRequest chatRequest) {
        return ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters.overrideWith(chatRequest.parameters()))
                .build();
    }

    private static String extractModelFromRequests(List<ChatRequest> requests) {
        var modelNames = requests.stream()
                .map(ChatRequest::modelName)
                .collect(Collectors.toUnmodifiableSet());
        if (modelNames.size() != 1) {
            throw new IllegalArgumentException("Batch requests cannot contain ChatRequest objects with different " +
                    "models; all requests must use the same model.");
        }

        return modelNames.iterator().next();
    }

    /**
     * Represents the possible states of a batch job.
     */
    public enum BatchJobState {
        JOB_STATE_PENDING,
        JOB_STATE_RUNNING,
        JOB_STATE_SUCCEEDED,
        JOB_STATE_FAILED,
        JOB_STATE_CANCELLED,
        JOB_STATE_EXPIRED,
        UNSPECIFIED
    }

    static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends GoogleAiGeminiChatModelBaseBuilder<Builder> {

        private Builder() {
        }

        public GoogleAiGeminiBatchChatModel build() {
            return new GoogleAiGeminiBatchChatModel(this);
        }
    }

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
                long priority) {
        }

        /**
         * Configures the input to the batch request.
         *
         * @param requests The list of inlined requests to be processed in the batch.
         */
        record InputConfig(Requests requests) {
        }

        /**
         * Wrapper for the list of inlined requests.
         *
         * @param requests The list of inlined requests to be processed in the batch.
         */
        record Requests(List<InlinedRequest> requests) {
        }

        /**
         * Individual request to be processed in the batch.
         *
         * @param request  Required. The {@link GeminiGenerateContentRequest} to be processed in the batch.
         * @param metadata Optional. The metadata to be associated with the request.
         */
        record InlinedRequest(GeminiGenerateContentRequest request,
                              Map<String, String> metadata) {
        }
    }

    public record BatchGenerateContentResponse() {
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
            @JsonProperty String name,
            @JsonProperty Map<String, Object> metadata,
            @JsonProperty boolean done,
            @JsonProperty Status error,
            @JsonProperty Map<String, Object> response
    ) {

        /**
         * Represents the error status of an operation.
         *
         * @param code    The status code.
         * @param message A developer-facing error message.
         * @param details A list of messages that carry the error details.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Status(
                @JsonProperty int code,
                @JsonProperty String message,
                @JsonProperty List<Map<String, Object>> details
        ) {
        }
    }
}
