package dev.langchain4j.model.mistralai.internal.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;

/**
 * A single line of the JSONL document referenced by a completed batch job's {@code output_file}
 * (or {@code error_file}).
 *
 * <p>{@link #customId} correlates the outcome with the originating request. Lines are returned in
 * arbitrary order, so callers must key on {@code custom_id} rather than position. A successful line
 * carries a {@link #response} whose {@link Response#body} is the {@code /v1/chat/completions} result;
 * a failed line carries an {@link #error}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiBatchResultEntry {

    public String customId;
    public Response response;
    public Map<String, Object> error;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class Response {

        public Integer statusCode;
        public MistralAiChatCompletionResponse body;
    }
}
