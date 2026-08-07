package dev.langchain4j.model.mistralai.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

/**
 * Request body for creating a batch job via the Mistral Batch API ({@code POST /v1/batch/jobs}).
 *
 * <p>Requests are submitted inline rather than by referencing an uploaded file. Each {@link Request}
 * pairs a {@code custom_id} with the body of a single {@code /v1/chat/completions} request; the
 * {@code custom_id} correlates the request with its result once the job has completed.</p>
 */
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class MistralAiBatchJobRequest {

    private final List<Request> requests;
    private final String endpoint;
    private final String model;
    private final Map<String, Object> metadata;
    private final Integer timeoutHours;

    private MistralAiBatchJobRequest(Builder builder) {
        this.requests = builder.requests;
        this.endpoint = builder.endpoint;
        this.model = builder.model;
        this.metadata = builder.metadata;
        this.timeoutHours = builder.timeoutHours;
    }

    public List<Request> getRequests() {
        return requests;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getModel() {
        return model;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Integer getTimeoutHours() {
        return timeoutHours;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<Request> requests;
        private String endpoint;
        private String model;
        private Map<String, Object> metadata;
        private Integer timeoutHours;

        private Builder() {}

        public Builder requests(List<Request> requests) {
            this.requests = requests;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder timeoutHours(Integer timeoutHours) {
            this.timeoutHours = timeoutHours;
            return this;
        }

        public MistralAiBatchJobRequest build() {
            return new MistralAiBatchJobRequest(this);
        }
    }

    /**
     * A single inline request within a batch job: a {@code custom_id} and the body of the underlying
     * {@code /v1/chat/completions} request.
     */
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class Request {

        private final String customId;
        private final MistralAiChatCompletionRequest body;

        public Request(String customId, MistralAiChatCompletionRequest body) {
            this.customId = customId;
            this.body = body;
        }

        public String getCustomId() {
            return customId;
        }

        public MistralAiChatCompletionRequest getBody() {
            return body;
        }
    }
}
