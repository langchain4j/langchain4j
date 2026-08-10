package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Request body for the Anthropic Message Batches API ({@code POST /v1/messages/batches}).
 *
 * <p>Each {@link Request} carries a caller-assigned {@code custom_id} and the same
 * {@link AnthropicCreateMessageRequest} that a single (non-batch) message request would use.</p>
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicCreateBatchRequest {

    public List<Request> requests;

    public AnthropicCreateBatchRequest() {}

    public AnthropicCreateBatchRequest(List<Request> requests) {
        this.requests = requests;
    }

    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class Request {

        public String customId;
        public AnthropicCreateMessageRequest params;

        public Request() {}

        public Request(String customId, AnthropicCreateMessageRequest params) {
            this.customId = customId;
            this.params = params;
        }
    }
}
