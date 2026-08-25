package dev.langchain4j.agentic.patterns.p2p;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class P2PPlannerTest {

    // ── Typed keys ──────────────────────────────────────────────────────

    public static class Key1 implements TypedKey<String> {
        @Override
        public String name() {
            return "key1";
        }
    }

    // ── Agent wrapper interfaces ────────────────────────────────────────
    // The @K parameter on the consumer agents tells P2PPlanner to wait until
    // key1 is present in scope before activating them.

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
    void should_terminate_when_no_agent_can_be_activated_anymore() throws Exception {
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

        ResultWithAgenticScope<String> result = invokeWithTimeout(p2p);

        assertThat(result.agenticScope().readState("key1", "")).isEqualTo("hello");
        assertThat(result.agenticScope().readState("result2", "")).isEqualTo("analyzed-hello");
    }

    @Test
    void should_not_terminate_while_an_agent_is_still_executing() throws Exception {
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

        ResultWithAgenticScope<String> result = invokeWithTimeout(p2p);

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
    private static ResultWithAgenticScope<String> invokeWithTimeout(UntypedAgent p2p) throws Exception {
        CompletableFuture<ResultWithAgenticScope<String>> future =
                CompletableFuture.supplyAsync(() -> p2p.invokeWithAgenticScope(Map.of()));
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            fail("P2P planner did not terminate when the agentic scope reached a stable state");
            throw e;
        }
    }
}
