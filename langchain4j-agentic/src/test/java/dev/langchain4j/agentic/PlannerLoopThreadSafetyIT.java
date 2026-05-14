package dev.langchain4j.agentic;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class PlannerLoopThreadSafetyIT {

    interface CountingAgent {
        @Agent
        int count();
    }

    /**
     * A planner that tracks per-agent follow-ups to expose the lost-update race
     * in {@code composeActions}:
     *
     * Phase 1: Launch all agents in parallel (firstAction).
     *          As each completes, the planner checks by agent ID whether it
     *          already triggered a follow-up. If not, it schedules one.
     *          All N callbacks race on the unsynchronized {@code nextAction} field
     *          in {@code onSubagentInvoked}.
     *
     * Phase 2: Execute whatever follow-ups survived the race.
     *          Each follow-up completion is tracked. Once all agents that
     *          need a follow-up have completed both phases, return done().
     *
     * The per-agent tracking ensures the planner always terminates (no hang
     * on lost actions) and allows precise counting of dropped follow-ups.
     */
    public static class ParallelBurstPlanner implements Planner {

        private Map<String, AgentInstance> agentsById;

        private final Set<String> phase1Completed = ConcurrentHashMap.newKeySet();

        @Override
        public void init(InitPlanningContext ctx) {
            this.agentsById = ctx.subagents().stream()
                    .collect(Collectors.toMap(AgentInstance::agentId, Function.identity()));
        }

        @Override
        public Action firstAction(PlanningContext ctx) {
            return call(new ArrayList<>(agentsById.values()));
        }

        @Override
        public Action nextAction(PlanningContext ctx) {
            AgentInvocation prev = ctx.previousAgentInvocation();
            String agentId = prev.agentId();

            if (phase1Completed.add(agentId)) {
                // First time this agent completed — phase 1 done, schedule follow-up.
                // All N phase-1 callbacks arrive concurrently from parallel threads.
                // Each returns a call() action that composeActions should merge,
                // but the lost-update race silently drops some.
                return call(agentsById.get(agentId));
            }

            // This agent already completed phase 1, so this is a follow-up
            // completion. All surviving follow-ups run in a single
            // parallelExecution batch. Returning done() from each is safe:
            // composeActions(done(), done()) = done(), so the loop terminates
            // after the batch completes regardless of how many survived.
            return done();
        }
    }

    /**
     * Launches 8 agents in parallel. Each completion schedules a follow-up
     * for that specific agent via {@code onSubagentInvoked → composeActions}.
     * All 8 callbacks race on the unsynchronized {@code nextAction} field.
     */
    @Test
    void parallel_burst_should_execute_all_followups() {
        int batchSize = 8;
        AtomicInteger executionCount = new AtomicInteger(0);

        Object[] subAgents = new Object[batchSize];
        for (int i = 0; i < batchSize; i++) {
            subAgents[i] = AgenticServices.agentAction(agenticScope -> {
                // Random sleep to increase interleaving and widen the race window
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
                executionCount.incrementAndGet();
            });
        }

        CountingAgent agent = AgenticServices.plannerBuilder(CountingAgent.class)
                .subAgents(subAgents)
                .planner(ParallelBurstPlanner::new)
                .output(scope -> executionCount.get())
                .build();

        int result = agent.count();

        // Each of the N agents should run once in phase 1, then once more as
        // a follow-up in phase 2, for a total of 2*N executions.
        // If the race condition triggers, some follow-ups are lost and result < 2*batchSize.
        assertThat(result)
                .as("All %d phase-1 agents and their %d follow-ups should have executed", batchSize, batchSize)
                .isEqualTo(2 * batchSize);
    }
}
