package dev.langchain4j.agentic.internal;

import java.util.Arrays;

public record AgentCall(String agentName, Object[] input, Object output) {

    @Override
    public String toString() {
        return "AgentInvocation{" +
                "agentName=" + agentName +
                ", input=" + Arrays.toString(input) +
                ", response=" + output +
                '}';
    }
}
