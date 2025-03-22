package dev.langchain4j.langfuse.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Score {
    @JsonProperty("id")
    private final String id;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("value")
    private final Object value;

    @JsonProperty("trace_id")
    private final String traceId;

    @JsonProperty("observation_id")
    private final String observationId;

    @JsonProperty("comment")
    private final String comment;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    private Score(Builder builder) {
        this.id = UUID.randomUUID().toString();
        this.name = builder.name;
        this.value = builder.value;
        this.traceId = builder.traceId;
        this.observationId = builder.observationId;
        this.comment = builder.comment;
        this.metadata = new HashMap<>(builder.metadata);
        this.timestamp = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getObservationId() {
        return observationId;
    }

    public String getComment() {
        return comment;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public static class Builder {
        private String name;
        private Object value;
        private String traceId;
        private String observationId;
        private String comment;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder observationId(String observationId) {
            this.observationId = observationId;
            return this;
        }

        public Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? metadata : new HashMap<>();
            return this;
        }

        public Score build() {
            return new Score(this);
        }
    }
}
