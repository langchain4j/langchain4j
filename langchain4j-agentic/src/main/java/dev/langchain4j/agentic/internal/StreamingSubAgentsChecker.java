package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.planner.AgentInstance;
import java.util.List;

public interface StreamingSubAgentsChecker {
    /**
     * Check the sub-agents whether is streaming or not
     * @param subAgents the sub-agents needed to be checked
     * @param plannerAgentOutputKey the planner agent outputKey
     * @return `true` is streaming or `false` is not
     */
    boolean isStreaming(List<AgentInstance> subAgents, String plannerAgentOutputKey);
}
