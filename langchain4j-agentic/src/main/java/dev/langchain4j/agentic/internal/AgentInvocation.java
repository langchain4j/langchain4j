package dev.langchain4j.agentic.internal;

import java.util.Map;

public record AgentInvocation(String agentName, Map<String, Object> input, Object output) {

    @Override
    public Object output() {
        return output instanceof AsyncResponse<?> asyncResponse ? asyncResponse.result() : output;
    }

    @Override
    public String toString() {
        return "AgentInvocation{" +
                "agentName=" + agentName +
                ", input=" + input +
                ", output=" + output +
                '}';
    }
}
