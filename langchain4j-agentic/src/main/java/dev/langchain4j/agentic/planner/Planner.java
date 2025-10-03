package dev.langchain4j.agentic.planner;

import dev.langchain4j.agentic.scope.AgentExecution;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.List;

public interface Planner {

    default void init(AgenticScope agenticScope, AgentInstance plannerAgent, List<AgentInstance> subagents) { }

    default Action firstAction(AgenticScope agenticScope) {
        return nextAction(agenticScope, null);
    }

    Action nextAction(AgenticScope agenticScope, AgentExecution previousAgentExecution);

    default Action noOp() {
        return Action.AgentCallAction.NO_OP;
    }

    default Action call(AgentInstance... agents) {
        return new Action.AgentCallAction(agents);
    }

    default Action call(List<AgentInstance> agents) {
        return call(agents.toArray(new AgentInstance[0]));
    }

    default Action done() {
        return Action.DoneAction.INSTANCE;
    }

    default Action done(Object result) {
        return new Action.DoneWithResultAction(result);
    }

}
