package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Result of an individual request within a batch.
 * The actual type depends on the processing outcome.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = AnthropicBatchResult.AnthropicBatchResultSuccess.class, name = "succeeded"),
    @JsonSubTypes.Type(value = AnthropicBatchResult.AnthropicBatchResultError.class, name = "errored"),
    @JsonSubTypes.Type(value = AnthropicBatchResult.AnthropicBatchResultCanceled.class, name = "canceled"),
    @JsonSubTypes.Type(value = AnthropicBatchResult.AnthropicBatchResultExpired.class, name = "expired")
})
public sealed interface AnthropicBatchResult
        permits AnthropicBatchResult.AnthropicBatchResultSuccess,
                AnthropicBatchResult.AnthropicBatchResultError,
                AnthropicBatchResult.AnthropicBatchResultCanceled,
                AnthropicBatchResult.AnthropicBatchResultExpired {

    /**
     * Successful result containing the message response.
     */
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    record AnthropicBatchResultSuccess(AnthropicCreateMessageResponse message) implements AnthropicBatchResult {}

    /**
     * Error result containing error details.
     */
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    record AnthropicBatchResultError(AnthropicBatchError error) implements AnthropicBatchResult {}

    /**
     * Result for a request that was canceled before processing.
     */
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    record AnthropicBatchResultCanceled() implements AnthropicBatchResult {}

    /**
     * Result for a request that expired before processing.
     */
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    record AnthropicBatchResultExpired() implements AnthropicBatchResult {}

    /**
     * Error details for a failed batch request.
     */
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    record AnthropicBatchError(String type, String message) {}
}
