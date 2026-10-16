package dev.langchain4j.agentic.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgenticScopeFunction;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentSkipped;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.patterns.debate.DebatePlanner;
import dev.langchain4j.agentic.patterns.goap.GoalOrientedPlanner;
import dev.langchain4j.agentic.patterns.p2p.P2PPlanner;
import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Tests how the planners of the patterns module deal with subagents skipped because their activation condition is
 * false.
 */
class SkippedSubagentsTest {

    private static final Predicate<AgenticScope> NEVER = scope -> false;

    private final AtomicInteger skippedInvocations = new AtomicInteger();

    private static AgenticScopeFunction<String> agent(String name, String output) {
        return AgenticServices.nonAiAgentBuilder(scope -> output)
                .name(name)
                .outputKey(name)
                .build();
    }

    private AgenticScopeFunction<String> inactiveAgent(String name, String output) {
        return AgenticServices.nonAiAgentBuilder(scope -> {
                    skippedInvocations.incrementAndGet();
                    return output;
                })
                .name(name)
                .outputKey(name)
                .activationCondition("never", NEVER)
                .build();
    }

    // ── Voting ──

    @Test
    void skipped_voter_does_not_vote() {
        UntypedAgent voting = AgenticServices.plannerBuilder()
                .subAgents(agent("voter1", "yes"), inactiveAgent("voter2", "no"), agent("voter3", "yes"))
                .outputKey("vote")
                .planner(VotingPlanner::new)
                .build();

        assertThat(voting.invoke(Map.of())).isEqualTo("yes");
        assertThat(skippedInvocations).hasValue(0);
    }

    @Test
    void voting_with_all_voters_skipped_has_no_result() {
        UntypedAgent voting = AgenticServices.plannerBuilder()
                .subAgents(inactiveAgent("voter1", "yes"), inactiveAgent("voter2", "no"))
                .outputKey("vote")
                .planner(VotingPlanner::new)
                .build();

        assertThat(voting.invoke(Map.of())).isNull();
        assertThat(skippedInvocations).hasValue(0);
    }

    // ── Debate ──

    @Test
    void skipped_debater_does_not_take_part_in_the_debate() {
        AtomicInteger judgeInvocations = new AtomicInteger();
        AgenticScopeFunction<String> judge = AgenticServices.nonAiAgentBuilder(scope -> {
                    judgeInvocations.incrementAndGet();
                    return "verdict on " + scope.readState(DebatePlanner.DEBATE_CONTEXT_KEY);
                })
                .name("judge")
                .outputKey("verdict")
                .build();

        UntypedAgent debate = AgenticServices.plannerBuilder()
                .subAgents(agent("optimist", "it works"), inactiveAgent("pessimist", "it fails"), judge)
                .outputKey("verdict")
                .planner(DebatePlanner::new)
                .build();

        // the only position is unanimous, so the judge is invoked after the first round
        assertThat(debate.invoke(Map.of())).isEqualTo("verdict on optimist: \"it works\"");
        assertThat(judgeInvocations).hasValue(1);
        assertThat(skippedInvocations).hasValue(0);
    }

    @Test
    void skipped_judge_ends_the_debate_with_the_current_value_of_its_output_key() {
        UntypedAgent debate = AgenticServices.plannerBuilder()
                .subAgents(agent("optimist", "it works"), agent("pessimist", "it works"), inactiveAgent("verdict", "?"))
                .outputKey("result")
                .planner(DebatePlanner::new)
                .build();

        assertThat(debate.invoke(Map.of("verdict", "previous verdict"))).isEqualTo("previous verdict");
        assertThat(skippedInvocations).hasValue(0);
    }

    // ── P2P and blackboard: a ping-pong where pong is inactive ──

    private final AtomicInteger pingInvocations = new AtomicInteger();
    private final AtomicInteger pongSkips = new AtomicInteger();

    private AgenticScopeFunction<String> ping() {
        return AgenticServices.nonAiAgentBuilder(scope -> {
                    pingInvocations.incrementAndGet();
                    return "ping-" + scope.readState("pong");
                })
                .name("ping")
                .inputKey(String.class, "pong")
                .outputKey("ping")
                .build();
    }

    private AgenticScopeFunction<String> inactivePong() {
        return pong(NEVER);
    }

    private AgenticScopeFunction<String> pong(Predicate<AgenticScope> activationCondition) {
        return AgenticServices.nonAiAgentBuilder(scope -> {
                    skippedInvocations.incrementAndGet();
                    return "pong-" + scope.readState("ping");
                })
                .name("pong")
                .inputKey(String.class, "ping")
                .outputKey("pong")
                .activationCondition("pong is needed", activationCondition)
                .listener(new AgentListener() {
                    @Override
                    public void onAgentSkipped(AgentSkipped agentSkipped) {
                        pongSkips.incrementAndGet();
                    }
                })
                .build();
    }

    /**
     * A condition holding only the first time it is evaluated: when the planner selects pong, and no more when pong
     * is invoked, as if the state changed in between.
     */
    private static Predicate<AgenticScope> holdingOnlyWhenSelected() {
        AtomicInteger evaluations = new AtomicInteger();
        return scope -> evaluations.incrementAndGet() == 1;
    }

    @Test
    void p2p_never_activates_an_inactive_agent() {
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(ping(), inactivePong())
                .planner(() -> new P2PPlanner(10))
                .build();

        ResultWithAgenticScope<String> result = system.invokeWithAgenticScope(Map.of("pong", "seed"));

        // the inactive pong is never selected, so it is never even skipped
        assertThat(pingInvocations).hasValue(1);
        assertThat(skippedInvocations).hasValue(0);
        assertThat(pongSkips).hasValue(0);
        assertThat(result.agenticScope().readState("pong")).isEqualTo("seed");
    }

    @Test
    void p2p_does_not_activate_other_agents_after_a_skip() {
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(ping(), pong(holdingOnlyWhenSelected()))
                .planner(() -> new P2PPlanner(10))
                .build();

        system.invoke(Map.of("pong", "seed"));

        // pong was selected but skipped, so its output didn't change and ping isn't activated again
        assertThat(pingInvocations).hasValue(1);
        assertThat(skippedInvocations).hasValue(0);
        assertThat(pongSkips).hasValue(1);
    }

    @Test
    void blackboard_does_not_activate_other_agents_after_a_skip() {
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(ping(), pong(holdingOnlyWhenSelected()))
                .outputKey("never-written")
                .planner(() -> new BlackboardPlanner(scope -> false, 5))
                .build();

        system.invoke(Map.of("pong", "seed"));

        // pong was selected but skipped, so its output didn't change and ping isn't activated again
        assertThat(pingInvocations).hasValue(1);
        assertThat(skippedInvocations).hasValue(0);
        assertThat(pongSkips).hasValue(1);
    }

    @Test
    void blackboard_never_selects_an_inactive_agent() {
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(ping(), inactivePong())
                .outputKey("never-written")
                .planner(() -> new BlackboardPlanner(scope -> false, 5))
                .build();

        system.invoke(Map.of("pong", "seed"));

        // the inactive pong is never selected, so it is never even skipped
        assertThat(pingInvocations).hasValue(1);
        assertThat(skippedInvocations).hasValue(0);
        assertThat(pongSkips).hasValue(0);
    }

    // ── GOAP ──

    private static AgenticScopeFunction<String> step(String name, String input, String output, AtomicInteger calls) {
        return AgenticServices.nonAiAgentBuilder(scope -> {
                    calls.incrementAndGet();
                    return name + "(" + scope.readState(input) + ")";
                })
                .name(name)
                .inputKey(String.class, input)
                .outputKey(output)
                .build();
    }

    @Test
    void goal_oriented_planner_plans_around_an_inactive_agent() {
        AtomicInteger calls = new AtomicInteger();
        // all the inputs of the producers of a key are required, so this agent would make the goal unreachable
        AgenticScopeFunction<String> approved = AgenticServices.nonAiAgentBuilder(scope -> {
                    skippedInvocations.incrementAndGet();
                    return "approved";
                })
                .name("approved")
                .inputKey(String.class, "approval")
                .outputKey("goal")
                .activationCondition("never", NEVER)
                .build();

        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(approved, step("first", "start", "middle", calls), step("second", "middle", "goal", calls))
                .outputKey("goal")
                .planner(GoalOrientedPlanner::new)
                .build();

        assertThat(system.invoke(Map.of("start", "s"))).isEqualTo("second(first(s))");
        assertThat(calls).hasValue(2);
        assertThat(skippedInvocations).hasValue(0);
    }

    @Test
    void goal_reachable_only_through_an_inactive_agent_is_unreachable() {
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(
                        step("first", "start", "middle", new AtomicInteger()),
                        AgenticServices.nonAiAgentBuilder(scope -> "goal")
                                .name("last")
                                .inputKey(String.class, "middle")
                                .outputKey("goal")
                                .activationCondition("never", NEVER)
                                .build())
                .outputKey("goal")
                .planner(GoalOrientedPlanner::new)
                .build();

        assertThatThrownBy(() -> system.invoke(Map.of("start", "s"))).hasStackTraceContaining("No path found for goal");
    }
}
