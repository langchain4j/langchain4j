package dev.langchain4j.langfuse.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Span {
    @JsonProperty("id")
    private final String id;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("start_time")
    private final Long startTime;

    @JsonProperty("end_time")
    private Long endTime;

    @JsonProperty("metadata")
    private final Map<String, Object> metadata;

    @JsonProperty("status")
    private String status;

    @JsonProperty("level")
    private String level;

    private Span(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : new HashMap<>();
        this.status = builder.status;
        this.level = builder.level;
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

    public Long getStartTime() {
        return startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public void end() {
        this.endTime = System.currentTimeMillis();
    }

    public static class Builder {
        private String id;
        private String name;
        private Long startTime;
        private Long endTime;
        private Map<String, Object> metadata;
        private String status;
        private String level;

        public Builder() {
            this.id = UUID.randomUUID().toString();
            this.startTime = System.currentTimeMillis();
            this.status = "success";
            this.level = "INFO";
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder startTime(Long startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Long endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder level(String level) {
            this.level = level;
            return this;
        }

        public Span build() {
            return new Span(this);
        }
    }
}
