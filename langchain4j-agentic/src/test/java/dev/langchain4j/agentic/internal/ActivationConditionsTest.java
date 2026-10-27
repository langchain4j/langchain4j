package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ActivationConditionsTest {

    private final DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
    private final PlanningContext planningContext = new PlanningContext(scope, null);

    private static AgentInstance subagentOf(Object agent) {
        UntypedAgent parent = AgenticServices.sequenceBuilder().subAgents(agent).build();
        return ((AgentInstance) parent).subagents().get(0);
    }

    private static AgentInstance leaf(Predicate<AgenticScope> condition) {
        var builder = AgenticServices.nonAiAgentBuilder(scope -> "leaf").name("leaf");
        return subagentOf(
                condition == null
                        ? builder.build()
                        : builder.activationCondition(condition).build());
    }

    private static AgentInstance composite(Predicate<AgenticScope> condition) {
        return subagentOf(AgenticServices.sequenceBuilder()
                .subAgents(AgenticServices.nonAiAgentBuilder(scope -> "inner").build())
                .activationCondition(condition)
                .build());
    }

    @Test
    void agent_without_condition_is_active() {
        assertThat(planningContext.isActive(leaf(null))).isTrue();
    }

    @Test
    void agent_is_active_when_its_condition_holds() {
        assertThat(planningContext.isActive(leaf(scope -> true))).isTrue();
        assertThat(planningContext.isActive(leaf(scope -> false))).isFalse();
    }

    @Test
    void failing_condition_is_considered_to_hold() {
        AgentInstance agent = leaf(scope -> {
            throw new IllegalStateException("broken condition");
        });

        assertThat(planningContext.isActive(agent)).isTrue();
    }

    @Test
    void activated_composite_agent_stays_active_until_it_completes() {
        AgentInstance agent = composite(scope -> false);
        assertThat(planningContext.isActive(agent)).isFalse();

        ActivationConditions.markActivated(agent, scope);
        assertThat(planningContext.isActive(agent)).isTrue();

        ActivationConditions.clearActivation(agent, scope);
        assertThat(planningContext.isActive(agent)).isFalse();
    }

    @Test
    void leaf_agent_is_never_marked_as_activated() {
        AgentInstance agent = leaf(scope -> false);

        ActivationConditions.markActivated(agent, scope);

        assertThat(scope.state()).isEmpty();
        assertThat(planningContext.isActive(agent)).isFalse();
    }
}
