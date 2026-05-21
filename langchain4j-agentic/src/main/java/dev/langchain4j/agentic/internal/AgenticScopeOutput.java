package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.TokenStream;

final class AgenticScopeOutput {

    private static final String STREAMING_OUTPUT_PREFIX = "__streaming_output_";
    private static final String ROOT_CALL_MARKER_KEY = "__streaming_output_root_call_marker";
    private static final Object NO_ROOT_CALL_MARKER = new Object();

    private AgenticScopeOutput() {}

    static void startRootCall(AgenticScope agenticScope) {
        agenticScope.writeExecutionContext(ROOT_CALL_MARKER_KEY, new Object());
    }

    static void write(AgenticScope agenticScope, String outputKey, Object output) {
        write(agenticScope, outputKey, output, false);
    }

    static void write(AgenticScope agenticScope, String outputKey, Object output, boolean runtimeStreamingOutput) {
        if (isBlank(outputKey)) {
            return;
        }
        if (output instanceof TokenStream || runtimeStreamingOutput) {
            agenticScope.writeState(outputKey, null);
            agenticScope.writeExecutionContext(
                    streamingOutputKey(outputKey), new StreamingOutput(currentRootCallMarker(agenticScope), output));
        } else {
            agenticScope.writeState(outputKey, output);
            agenticScope.writeExecutionContext(
                    streamingOutputKey(outputKey), new StreamingOutput(currentRootCallMarker(agenticScope), null));
        }
    }

    static Object read(AgenticScope agenticScope, String outputKey, Object defaultOutput) {
        if (isBlank(outputKey)) {
            return defaultOutput;
        }
        Object output = agenticScope.readState(outputKey);
        if (output == null) {
            output = streamingOutput(agenticScope, outputKey);
            if (output instanceof DelayedResponse<?> delayedResponse) {
                output = delayedResponse.blockingGet();
                writeResolvedState(agenticScope, outputKey, output);
            }
        }
        return output != null ? output : defaultOutput;
    }

    static Object persistentOutput(Object output) {
        if (output instanceof TokenStream) {
            return null;
        }
        if (output instanceof DelayedResponse<?> delayedResponse) {
            return persistentOutput(delayedResponse.result());
        }
        return output;
    }

    static Object invocationOutput(Object output) {
        return output instanceof DelayedResponse<?> ? output : persistentOutput(output);
    }

    static Object invocationOutput(Object output, boolean runtimeStreamingOutput) {
        return runtimeStreamingOutput ? null : invocationOutput(output);
    }

    static Object writeResolvedState(AgenticScope agenticScope, String outputKey, Object output) {
        write(agenticScope, outputKey, output);
        return output;
    }

    private static Object streamingOutput(AgenticScope agenticScope, String outputKey) {
        Object output = agenticScope.executionContext(streamingOutputKey(outputKey));
        if (output instanceof StreamingOutput streamingOutput
                && streamingOutput.rootCallMarker() == currentRootCallMarker(agenticScope)) {
            return streamingOutput.output();
        }
        return null;
    }

    private static String streamingOutputKey(String outputKey) {
        return STREAMING_OUTPUT_PREFIX + outputKey;
    }

    private static Object currentRootCallMarker(AgenticScope agenticScope) {
        Object marker = agenticScope.executionContext(ROOT_CALL_MARKER_KEY);
        return marker != null ? marker : NO_ROOT_CALL_MARKER;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record StreamingOutput(Object rootCallMarker, Object output) {}
}
