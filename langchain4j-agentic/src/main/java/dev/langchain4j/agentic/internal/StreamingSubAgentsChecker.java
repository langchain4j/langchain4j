package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.planner.AgentInstance;
import java.util.List;

public interface StreamingSubAgentsChecker {
    void checkSubAgents(List<AgentInstance> subAgents, AgentInstance plannerAgent);
}
