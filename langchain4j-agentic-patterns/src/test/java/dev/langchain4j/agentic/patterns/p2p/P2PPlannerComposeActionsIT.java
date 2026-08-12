package dev.langchain4j.agentic.patterns.p2p;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class P2PPlannerComposeActionsIT {

    // ── Typed keys ──────────────────────────────────────────────────────

    public static class Key1 implements TypedKey<String> {
        @Override public String name() { return "key1"; }
    }

    public static class Result2 implements TypedKey<String> {
        @Override public String name() { return "result2"; }
    }

    public static class Result3 implements TypedKey<String> {
        @Override public String name() { return "result3"; }
    }

    // ── Agent wrapper interfaces ────────────────────────────────────────
    // The @K parameter on Consumer agents tells P2PPlanner to wait until
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
    void p2p_should_not_freeze_when_parallel_agents_complete() throws Exception {
        Object agent1 = AgenticServices.conditionalBuilder(ProducerAgent.class)
                .subAgents(
                        "Run when key1 absent",
                        scope -> !scope.hasState("key1"),
                        AgenticServices.agentAction(scope -> scope.writeState("key1", "hello")))
                .name("agent1")
                .build();

        Object agent2 = AgenticServices.conditionalBuilder(Consumer2Agent.class)
                .subAgents(
                        "Run when result2 absent",
                        scope -> !scope.hasState("result2"),
                        AgenticServices.agentAction(scope -> {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
                            scope.writeState("result2", "analyzed-" + scope.readState("key1", ""));
                        }))
                .name("agent2")
                .build();

        Object agent3 = AgenticServices.conditionalBuilder(Consumer3Agent.class)
                .subAgents(
                        "Run when result3 absent",
                        scope -> !scope.hasState("result3"),
                        AgenticServices.agentAction(scope -> {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
                            scope.writeState("result3", "poem-about-" + scope.readState("key1", ""));
                        }))
                .name("agent3")
                .build();

        UntypedAgent p2p = AgenticServices.plannerBuilder()
                .subAgents(agent1, agent2, agent3)
                .planner(() -> new P2PPlanner(15, (scope, invocations) ->
                        scope.hasState("result2") && scope.hasState("result3")))
                .name("p2p-test")
                .build();

        // Run on a daemon thread so the test doesn't hang forever if the bug triggers
        CompletableFuture<ResultWithAgenticScope<String>> future = CompletableFuture.supplyAsync(
                () -> p2p.invokeWithAgenticScope(Map.of()));

        try {
            ResultWithAgenticScope<String> result = future.get(5, TimeUnit.SECONDS);

            assertThat(result.agenticScope().readState("key1", "")).isEqualTo("hello");
            assertThat(result.agenticScope().readState("result2", "")).isEqualTo("analyzed-hello");
            assertThat(result.agenticScope().readState("result3", "")).isEqualTo("poem-about-hello");
        } catch (TimeoutException e) {
            future.cancel(true);
            fail("P2P planner froze: composeActions likely dropped a done() action in favor of an empty call()");
        }
    }
}
