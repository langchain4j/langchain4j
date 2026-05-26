package dev.langchain4j.agentic.scope;

import dev.langchain4j.agentic.internal.DelayedResponse;
import dev.langchain4j.service.TokenStream;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentInvocation(
        Class<?> agentType, String agentName, String agentId, Map<String, Object> input, Object output) {

    AgentInvocation persistentCopy() {
        return new AgentInvocation(
                agentType, agentName, agentId, persistentInput(), AgenticScopePersistentValue.sanitize(output));
    }

    private Map<String, Object> persistentInput() {
        if (input == null) {
            return null;
        }
        boolean changed = false;
        Map<String, Object> persistentInput = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            Object value = entry.getValue();
            Object persistentValue = AgenticScopePersistentValue.sanitize(value);
            changed |= persistentValue != value;
            persistentInput.put(entry.getKey(), persistentValue);
        }
        return changed ? persistentInput : input;
    }

    @Override
    public Object output() {
        return persistentValue(output);
    }

    private static Object persistentValue(Object value) {
        Object result = value instanceof DelayedResponse<?> delayedResponse ? delayedResponse.result() : value;
        return result instanceof TokenStream ? null : result;
    }

    @Override
    public String toString() {
        return "AgentInvocation{" + "agentName=" + agentName + ", input=" + input + ", output=" + output + '}';
    }
}
