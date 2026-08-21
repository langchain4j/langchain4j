package dev.langchain4j.agentic.patterns.p2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class P2PPlannerTest {

    // Two non-AI agents consuming each other's output: exactly one of them can be activated at a time,
    // and neither ever runs out of work, so the invocations cap is the only termination condition.

    private final AtomicInteger invocations = new AtomicInteger();

    private final Object pingAgent = new Object() {
        @Agent(outputKey = "ping")
        public String ping(@V("pong") String pong) {
            invocations.incrementAndGet();
            return "ping-" + pong;
        }
    };

    private final Object pongAgent = new Object() {
        @Agent(outputKey = "pong")
        public String pong(@V("ping") String ping) {
            invocations.incrementAndGet();
            return "pong-" + ping;
        }
    };

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 6, 10})
    void shouldNotInvokeMoreAgentsThanMaxAgentsInvocations(int maxAgentsInvocations) {
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(pingAgent, pongAgent)
                .planner(() -> new P2PPlanner(maxAgentsInvocations))
                .build();

        invokeWithTimeout(system);

        assertThat(invocations.get()).isEqualTo(maxAgentsInvocations);
    }

    @Test
    void shouldStopBeforeMaxAgentsInvocationsWhenExitConditionIsMet() {
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(pingAgent, pongAgent)
                .planner(() -> new P2PPlanner(10, (scope, invocationCounter) -> invocationCounter >= 2))
                .build();

        invokeWithTimeout(system);

        assertThat(invocations.get()).isEqualTo(2);
    }

    // Run on a separate thread so that a planner that never terminates fails the test instead of hanging the build.
    private static void invokeWithTimeout(UntypedAgent system) {
        CompletableFuture<?> future =
                CompletableFuture.supplyAsync(() -> system.invokeWithAgenticScope(Map.of("pong", "seed")));
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            fail("P2P planner did not terminate");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
