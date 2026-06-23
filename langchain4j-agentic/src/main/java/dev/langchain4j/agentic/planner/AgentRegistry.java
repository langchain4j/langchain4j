package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Collection;

public interface AgentRegistry {

    Collection<AgentInstance> discoverAgents(AgenticScope scope);
}
