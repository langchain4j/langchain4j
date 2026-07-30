package dev.langchain4j.model.anthropic.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * A single line of the JSONL document served from a batch's {@code results_url}.
 *
 * <p>{@link #customId} correlates the outcome with the originating request. Results are returned
 * in arbitrary order, so callers must key on {@code custom_id} rather than position.
 * {@code result.type} is one of {@code succeeded}, {@code errored}, {@code canceled}, or
 * {@code expired}; {@link Result#message} is populated only for {@code succeeded}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicBatchResult {

    public String customId;
    public Result result;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class Result {

        public String type;
        public AnthropicCreateMessageResponse message;
        public ResultError error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class ResultError {

        public String type;
        public AnthropicError error;
    }
}
