package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoopPlannerTest {

    @Test
    void should_stop_after_max_iterations() {
        int maxIterations = 3;
        BiPredicate<AgenticScope, Integer> exitCondition = (scope, iter) -> false;

        LoopPlanner planner = new LoopPlanner(maxIterations, true, exitCondition, "test");
        
        InitPlanningContext initContext = mock(InitPlanningContext.class);
        AgentInstance agent = mock(AgentInstance.class);
        when(initContext.subagents()).thenReturn(List.of(agent));
        
        planner.init(initContext);
        
        PlanningContext context = mock(PlanningContext.class);
        AgenticScope scope = mock(AgenticScope.class);
        when(context.agenticScope()).thenReturn(scope);

        // Initial call
        Action firstAction = planner.firstAction(context);
        assertThat(firstAction.isDone()).isFalse();

        int loopCount = 0;
        Action currentAction = firstAction;
        
        // Loop continuously until done
        int maxSafetyCounter = 100;
        while (!currentAction.isDone() && maxSafetyCounter-- > 0) {
            loopCount++;
            currentAction = planner.nextAction(context);
        }

        // For maxIterations=3, the agent should be called exactly 3 times
        // 1st time: firstAction -> loopCount = 1 -> nextAction called
        // 2nd time: nextAction returns call() -> loopCount = 2 -> nextAction called
        // 3rd time: nextAction returns call() -> loopCount = 3 -> nextAction called
        // 4th time: nextAction should return done()
        assertThat(loopCount).isEqualTo(maxIterations);
        assertThat(currentAction.isDone()).isTrue();
    }
}
