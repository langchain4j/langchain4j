package dev.langchain4j.agentic.scope;

import dev.langchain4j.agentic.internal.DelayedResponse;
import java.util.Map;

public record AgentInvocation(
        Class<?> agentType, String agentName, String agentId, Map<String, Object> input, Object output) {

    @Override
    public Object output() {
        return output instanceof DelayedResponse<?> delayedResponse ? delayedResponse.result() : output;
    }

    @Override
    public String toString() {
        return "AgentInvocation{" + "agentName=" + agentName + ", input=" + input + ", output=" + output + '}';
    }
}
