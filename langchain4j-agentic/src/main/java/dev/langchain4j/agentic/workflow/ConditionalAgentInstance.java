package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.planner.AgentInstance;
import java.util.List;

public interface ConditionalAgentInstance extends AgentInstance {
    List<ConditionalAgent> conditionalSubagents();
}
