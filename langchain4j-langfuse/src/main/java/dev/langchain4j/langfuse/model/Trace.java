package dev.langchain4j.langfuse.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Trace {
    private final String id;
    private final String name;
    private final Instant startTime;
    private Instant endTime;
    private final Map<String, Object> inputs;
    private Map<String, Object> outputs;
    private String status;
    private String userId;
    private final Map<String, Object> metadata;
    private String version;
    private String projectId;

    private Trace(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.inputs = builder.inputs;
        this.outputs = builder.outputs;
        this.status = builder.status;
        this.userId = builder.userId;
        this.metadata = builder.metadata;
        this.version = builder.version;
        this.projectId = builder.projectId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public String getStatus() {
        return status;
    }

    public String getUserId() {
        return userId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getVersion() {
        return version;
    }

    public String getProjectId() {
        return projectId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private Instant startTime;
        private Instant endTime;
        private Map<String, Object> inputs = new HashMap<>();
        private Map<String, Object> outputs = new HashMap<>();
        private String status;
        private String userId;
        private Map<String, Object> metadata = new HashMap<>();
        private String version;
        private String projectId;

        public Builder() {
            this.id = UUID.randomUUID().toString();
            this.startTime = Instant.now();
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(Instant endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder inputs(Map<String, Object> inputs) {
            this.inputs = inputs;
            return this;
        }

        public Builder input(String key, Object value) {
            this.inputs.put(key, value);
            return this;
        }

        public Builder outputs(Map<String, Object> outputs) {
            this.outputs = outputs;
            return this;
        }

        public Builder output(String key, Object value) {
            this.outputs.put(key, value);
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Trace build() {
            return new Trace(this);
        }
    }
}
