package dev.langchain4j.agentic.internal;

import java.util.Arrays;

public record AgentInvocation(String agentName, Object[] input, Object output) {

    @Override
    public Object output() {
        return output instanceof AsyncResponse<?> asyncResponse ? asyncResponse.result() : output;
    }

    @Override
    public String toString() {
        return "AgentInvocation{" +
                "agentName=" + agentName +
                ", input=" + Arrays.toString(input) +
                ", output=" + output +
                '}';
    }
}
