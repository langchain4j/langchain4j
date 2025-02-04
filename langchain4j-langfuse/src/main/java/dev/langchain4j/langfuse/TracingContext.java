package dev.langchain4j.langfuse;

import dev.langchain4j.langfuse.model.Observation;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class TracingContext {
    private final String traceId;
    private final Stack<Observation> observationStack;
    private final Map<String, Object> metadata;
    private String sessionId;

    public TracingContext(Builder builder) {
        this.traceId = builder.traceId;
        this.observationStack = builder.observationStack != null ? builder.observationStack : new Stack<>();
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : new HashMap<>();
        this.sessionId = builder.sessionId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTraceId() {
        return traceId;
    }

    public Stack<Observation> getObservationStack() {
        return observationStack;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void pushObservation(Observation observation) {
        observationStack.push(observation);
    }

    public Observation popObservation() {
        return observationStack.isEmpty() ? null : observationStack.pop();
    }

    public Observation getCurrentObservation() {
        return observationStack.isEmpty() ? null : observationStack.peek();
    }

    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public static class Builder {
        private String traceId;
        private Stack<Observation> observationStack;
        private Map<String, Object> metadata;
        private String sessionId;

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder observationStack(Stack<Observation> observationStack) {
            this.observationStack = observationStack;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public TracingContext build() {
            return new TracingContext(this);
        }
    }
}
