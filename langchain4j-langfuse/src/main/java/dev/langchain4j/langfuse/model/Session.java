package dev.langchain4j.langfuse.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Session {
    @JsonProperty("id")
    private final String id;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    @JsonProperty("start_time")
    private final Instant startTime;

    @JsonProperty("end_time")
    private Instant endTime;

    @JsonProperty("user_id")
    private String userId;

    private Session(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.metadata = builder.metadata;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.userId = builder.userId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void end() {
        this.endTime = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private Map<String, Object> metadata;
        private Instant startTime;
        private Instant endTime;
        private String userId;

        public Builder() {}

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder addMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder setStartTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder setEndTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder setUserId(String userId) {
            this.userId = userId;
            return this;
        }

        public Session build() {
            return new Session(this);
        }
    }
}
