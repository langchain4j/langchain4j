package dev.langchain4j.langfuse;

import dev.langchain4j.langfuse.model.Generation;
import java.util.Map;

public interface LangfuseTracer {
    // Trace operations
    String startTrace(String name, Map<String, Object> input, String status);

    void endTrace(String traceId, Map<String, Object> output, String status);

    // Observation operations
    String logEvent(String traceId, String name, Map<String, Object> data);

    String startSpan(String traceId, String name, Map<String, Object> input, String status);

    String startSpan(String traceId, String name, Map<String, Object> input, String parentSpanId, String status);

    void endSpan(String spanId, Map<String, Object> output, String status);

    void updateSpan(String spanId, Map<String, Object> updates, String status);

    String logGeneration(String traceId, Generation params);

    void setTag(String traceId, String name, String value);

    // Session operations
    String createSession(String name, Map<String, Object> metadata);

    void addTraceToSession(String sessionId, String traceId);

    // Scoring operations
    void scoreTrace(String traceId, String name, Object value);

    void scoreObservation(String traceId, String observationId, String name, Object value);

    // Resource management
    void flush();

    void shutdown();
}
