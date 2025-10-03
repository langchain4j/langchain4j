package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.List;

public interface Planner {

    default void init(AgenticScope agenticScope, List<AgentInstance> agents) { }

    Action firstAction(AgenticScope agenticScope);

    default Action nextAction(AgenticScope agenticScope, AgentExecution lastAgentExecution) {
        return Action.done();
    }
}
