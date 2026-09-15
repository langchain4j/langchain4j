package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import org.junit.jupiter.api.Test;

class PlannerInitFailureTest {

    public static class FailingInitPlanner implements Planner {

        @Override
        public void init(InitPlanningContext initPlanningContext) {
            throw new IllegalStateException("planner initialization failed");
        }

        @Override
        public Action nextAction(PlanningContext planningContext) {
            return done();
        }
    }

    public interface FailingPlannerWorkflow {

        @Agent
        String run();
    }

    private static FailingPlannerWorkflow workflowWith(AgentMonitor monitor) {
        return AgenticServices.plannerBuilder(FailingPlannerWorkflow.class)
                .subAgents(AgenticServices.agentAction(agenticScope -> agenticScope.writeState("unused", "value")))
                .planner(FailingInitPlanner::new)
                .listener(monitor)
                .build();
    }

    @Test
    void ephemeral_agentic_scope_is_evicted_when_planner_init_fails() {
        AgentMonitor monitor = new AgentMonitor();
        FailingPlannerWorkflow workflow = workflowWith(monitor);

        assertThatThrownBy(workflow::run).isInstanceOf(IllegalStateException.class);

        AgenticScopeRegistry registry = ((AgenticScopeOwner) workflow).registry();
        assertThat(registry.getAllAgenticScopeKeysInMemory()).isEmpty();
    }

    @Test
    void monitor_has_no_ongoing_execution_when_planner_init_fails() {
        AgentMonitor monitor = new AgentMonitor();
        FailingPlannerWorkflow workflow = workflowWith(monitor);

        assertThatThrownBy(workflow::run).isInstanceOf(IllegalStateException.class);

        assertThat(monitor.ongoingExecutions()).isEmpty();
    }
}
