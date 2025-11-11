package dev.langchain4j.agentic;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlannerRequest;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomPlannerIT {

    interface MathAgent {
        @Agent
        int doMath();
    }

    public static class ParallelInPairsPlanner implements Planner {

        private List<AgentInstance> agents;
        private int cursor = 0;
        private int onGoingRequests = 1;

        private final ReentrantLock lock = new ReentrantLock();

        private final List<Integer> invocations;

        public ParallelInPairsPlanner(List<Integer> invocations) {
            this.invocations = invocations;
        }

        @Override
        public void init(AgenticScope agenticScope, AgentInstance plannerAgent, List<AgentInstance> subagents) {
            this.agents = subagents;
        }

        @Override
        public Action nextAction(PlannerRequest plannerRequest) {
            lock.lock();
            try {
                if (--onGoingRequests == 0) {
                    int missingRequests = agents.size() - cursor;
                    int requestsToMake = Math.min(2, missingRequests);
                    if (requestsToMake == 0) {
                        return done();
                    }
                    onGoingRequests = requestsToMake;
                    List<AgentInstance> toCall = agents.subList(cursor, cursor + requestsToMake);
                    cursor += requestsToMake;
                    invocations.add(requestsToMake);
                    return call(toCall);
                }
                return noOp();
            } finally {
                lock.unlock();
            }
        }
    }

    @Test
    void parallel_in_pairs_tests() {
        List<Integer> invocations = new ArrayList<>();

        MathAgent mathAgent = AgenticServices.plannerBuilder(MathAgent.class)
                .subAgents(AgenticServices.agentAction( agenticScope -> {
                    Thread.sleep(4);
                    agenticScope.writeState("threadA", 5);
                }), AgenticServices.agentAction( agenticScope -> {
                    Thread.sleep(2);
                    agenticScope.writeState("threadB", 10);
                }), AgenticServices.agentAction( agenticScope -> {
                    Thread.sleep(1);
                    agenticScope.writeState("threadA", agenticScope.readState("threadA", 0) * 2);
                }), AgenticServices.agentAction( agenticScope -> {
                    Thread.sleep(3);
                    agenticScope.writeState("threadB", agenticScope.readState("threadB", 0) * 2);
                }), AgenticServices.agentAction( agenticScope -> {
                    agenticScope.writeState("result", agenticScope.readState("threadA", 0) + agenticScope.readState("threadB", 0));
                }))
                .outputKey("result")
                .planner(() -> new ParallelInPairsPlanner(invocations))
                .build();

        int result = mathAgent.doMath();
        assertThat(result).isEqualTo(30);
        assertThat(invocations).containsExactly(2, 2, 1);
    }
}
