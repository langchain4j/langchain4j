package dev.langchain4j.agentic.planner;

import java.util.List;

public interface Planner {

    default void init(InitPlanningContext initPlanningContext) { }

    default Action firstAction(PlanningContext planningContext) {
        return nextAction(planningContext);
    }

    Action nextAction(PlanningContext planningContext);

    default Action noOp() {
        return Action.NoOpAction.INSTANCE;
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
