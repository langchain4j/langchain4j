package dev.langchain4j.langfuse.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Observation {
    private final String id;
    private final String name;
    private final String traceId;
    private final String parentObservationId;
    private final String level;
    private final String statusMessage;
    private final String version;
    private final String status;
    private final Map<String, Object> metadata;
    private final Map<String, Object> input;
    private final Map<String, Object> output;
    private final Instant startTime;
    private final Instant endTime;
    private final ObservationType type;

    private Observation(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.traceId = builder.traceId;
        this.parentObservationId = builder.parentObservationId;
        this.level = builder.level;
        this.statusMessage = builder.statusMessage;
        this.version = builder.version;
        this.metadata = new HashMap<>(builder.metadata);
        this.input = new HashMap<>(builder.input);
        this.output = new HashMap<>(builder.output);
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.status = builder.status;
        this.type = builder.type;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getParentObservationId() {
        return parentObservationId;
    }

    public String getLevel() {
        return level;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    public Map<String, Object> getInput() {
        return new HashMap<>(input);
    }

    public Map<String, Object> getOutput() {
        return new HashMap<>(output);
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String traceId;
        private String parentObservationId;
        private String status;
        private String level = "DEFAULT";
        private String statusMessage;
        private String version = "1.0.0";
        private Map<String, Object> metadata = new HashMap<>();
        private Map<String, Object> input = new HashMap<>();
        private Map<String, Object> output = new HashMap<>();
        private Instant startTime = Instant.now();
        private Instant endTime;
        private ObservationType type;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder parentObservationId(String parentObservationId) {
            this.parentObservationId = parentObservationId;
            return this;
        }

        public Builder level(String level) {
            this.level = level;
            return this;
        }

        public Builder statusMessage(String statusMessage) {
            this.statusMessage = statusMessage;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder input(Map<String, Object> input) {
            this.input = new HashMap<>(input);
            return this;
        }

        public Builder addInput(String key, Object value) {
            this.input.put(key, value);
            return this;
        }

        public Builder output(Map<String, Object> output) {
            this.output = new HashMap<>(output);
            return this;
        }

        public Builder addOutput(String key, Object value) {
            this.output.put(key, value);
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

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder type(ObservationType type) {
            this.type = type;
            return this;
        }

        public Observation build() {
            validate();
            return new Observation(this);
        }

        private void validate() {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalStateException("Name is required");
            }
            if (traceId == null || traceId.trim().isEmpty()) {
                throw new IllegalStateException("TraceId is required");
            }
            if (startTime == null) {
                throw new IllegalStateException("StartTime is required");
            }
            if (endTime != null && endTime.isBefore(startTime)) {
                throw new IllegalStateException("EndTime cannot be before StartTime");
            }
        }
    }

    @Override
    public String toString() {
        return "Observation{"
                + "id='"
                + id
                + '\''
                + ", name='"
                + name
                + '\''
                + ", traceId='"
                + traceId
                + '\''
                + ", parentObservationId='"
                + parentObservationId
                + '\''
                + ", level='"
                + level
                + '\''
                + ", statusMessage='"
                + statusMessage
                + '\''
                + ", version='"
                + version
                + '\''
                + ", metadata="
                + metadata
                + ", input="
                + input
                + ", output="
                + output
                + ", startTime="
                + startTime
                + ", endTime="
                + endTime
                + '}';
    }
}
