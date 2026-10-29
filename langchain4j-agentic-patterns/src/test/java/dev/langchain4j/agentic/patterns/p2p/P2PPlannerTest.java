package dev.langchain4j.agentic.patterns.p2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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

        invokeWithTimeout(system, Map.of("pong", "seed"));

        assertThat(invocations.get()).isEqualTo(maxAgentsInvocations);
    }

    @Test
    void shouldStopBeforeMaxAgentsInvocationsWhenExitConditionIsMet() {
        UntypedAgent system = AgenticServices.plannerBuilder()
                .subAgents(pingAgent, pongAgent)
                .planner(() -> new P2PPlanner(10, (scope, invocationCounter) -> invocationCounter >= 2))
                .build();

        invokeWithTimeout(system, Map.of("pong", "seed"));

        assertThat(invocations.get()).isEqualTo(2);
    }

    public static class Key1 implements TypedKey<String> {
        @Override
        public String name() {
            return "key1";
        }
    }

    public interface ProducerAgent {
        @Agent
        void produce();
    }

    public interface Consumer2Agent {
        @Agent
        void consume(@K(Key1.class) String key1);
    }

    public interface Consumer3Agent {
        @Agent
        void consume(@K(Key1.class) String key1);
    }

    @Test
    void should_terminate_when_no_agent_can_be_activated_anymore() {
        Object producer = producerAgent();

        Object consumer = AgenticServices.conditionalBuilder(Consumer2Agent.class)
                .subAgents(
                        "Run when result2 absent",
                        scope -> !scope.hasState("result2"),
                        AgenticServices.agentAction(
                                scope -> scope.writeState("result2", "analyzed-" + scope.readState("key1", ""))))
                .name("consumer2")
                .build();

        // No exit condition: the planner can only stop when the agentic scope reaches a stable state.
        UntypedAgent p2p = AgenticServices.plannerBuilder()
                .subAgents(producer, consumer)
                .planner(() -> new P2PPlanner(15))
                .name("p2p-quiescence-test")
                .build();

        ResultWithAgenticScope<String> result = invokeWithTimeout(p2p, Map.of());

        assertThat(result.agenticScope().readState("key1", "")).isEqualTo("hello");
        assertThat(result.agenticScope().readState("result2", "")).isEqualTo("analyzed-hello");
    }

    @Test
    void should_not_terminate_while_an_agent_is_still_executing() {
        // consumer3 completes first and only then releases consumer2, so the planner is asked for the next
        // action while consumer2 is still running. It must not consider the scope stable at that point.
        CountDownLatch consumer3Completed = new CountDownLatch(1);

        Object producer = producerAgent();

        Object slowConsumer = AgenticServices.conditionalBuilder(Consumer2Agent.class)
                .subAgents(
                        "Run when result2 absent",
                        scope -> !scope.hasState("result2"),
                        AgenticServices.agentAction(scope -> {
                            assertThat(consumer3Completed.await(5, TimeUnit.SECONDS))
                                    .isTrue();
                            scope.writeState("result2", "analyzed-" + scope.readState("key1", ""));
                        }))
                .name("consumer2")
                .build();

        Object fastConsumer = AgenticServices.conditionalBuilder(Consumer3Agent.class)
                .subAgents(
                        "Run when result3 absent",
                        scope -> !scope.hasState("result3"),
                        AgenticServices.agentAction(scope -> {
                            scope.writeState("result3", "poem-about-" + scope.readState("key1", ""));
                            consumer3Completed.countDown();
                        }))
                .name("consumer3")
                .build();

        UntypedAgent p2p = AgenticServices.plannerBuilder()
                .subAgents(producer, slowConsumer, fastConsumer)
                .planner(() -> new P2PPlanner(15))
                .name("p2p-parallel-test")
                .build();

        ResultWithAgenticScope<String> result = invokeWithTimeout(p2p, Map.of());

        assertThat(result.agenticScope().readState("result2", "")).isEqualTo("analyzed-hello");
        assertThat(result.agenticScope().readState("result3", "")).isEqualTo("poem-about-hello");
    }

    private static Object producerAgent() {
        return AgenticServices.conditionalBuilder(ProducerAgent.class)
                .subAgents(
                        "Run when key1 absent",
                        scope -> !scope.hasState("key1"),
                        AgenticServices.agentAction(scope -> scope.writeState("key1", "hello")))
                .name("producer")
                .build();
    }

    // Run on a separate thread so that a planner that never terminates fails the test instead of hanging the build.
    private static ResultWithAgenticScope<String> invokeWithTimeout(UntypedAgent p2p, Map<String, Object> args) {
        CompletableFuture<ResultWithAgenticScope<String>> future =
                CompletableFuture.supplyAsync(() -> p2p.invokeWithAgenticScope(args));
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            fail("P2P planner did not terminate");
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
