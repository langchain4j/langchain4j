package dev.langchain4j.agentic.supervisor;

import java.util.Map;

public class AgentInvocation {

    private String agentName;
    private Map<String, Object> arguments;

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(final String agentName) {
        this.agentName = agentName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(final Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    @Override
    public String toString() {
        return "AgentInvocation{" +
                "agentName='" + agentName + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
