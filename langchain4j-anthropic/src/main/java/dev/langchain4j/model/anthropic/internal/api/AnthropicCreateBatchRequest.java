package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.List;

/**
 * Request to create a new Message Batch.
 *
 * @param requests list of batch requests to process
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record AnthropicCreateBatchRequest(List<AnthropicBatchRequest> requests) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<AnthropicBatchRequest> requests = new ArrayList<>();

        public Builder requests(List<AnthropicBatchRequest> requests) {
            this.requests.clear();
            this.requests.addAll(requests);
            return this;
        }

        public Builder addRequest(AnthropicBatchRequest request) {
            this.requests.add(request);
            return this;
        }

        public AnthropicCreateBatchRequest build() {
            return new AnthropicCreateBatchRequest(List.copyOf(requests));
        }
    }
}
