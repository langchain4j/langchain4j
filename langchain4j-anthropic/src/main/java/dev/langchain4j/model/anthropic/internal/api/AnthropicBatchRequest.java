package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Represents a single request within a Message Batch.
 *
 * @param customId a unique identifier for this request within the batch
 * @param params the standard Messages API parameters
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public record AnthropicBatchRequest(String customId, AnthropicCreateMessageRequest params) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String customId;
        private AnthropicCreateMessageRequest params;

        public Builder customId(String customId) {
            this.customId = customId;
            return this;
        }

        public Builder params(AnthropicCreateMessageRequest params) {
            this.params = params;
            return this;
        }

        public AnthropicBatchRequest build() {
            return new AnthropicBatchRequest(customId, params);
        }
    }
}
