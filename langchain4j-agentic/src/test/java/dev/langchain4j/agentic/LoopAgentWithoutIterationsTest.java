package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.service.V;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LoopAgentWithoutIterationsTest {

    public static class CountingAgent {

        final AtomicInteger invocations = new AtomicInteger();

        @Agent(outputKey = "story")
        public String write(@V("topic") String topic) {
            invocations.incrementAndGet();
            return "a story about " + topic;
        }
    }

    @Test
    void zero_max_iterations_does_not_invoke_the_subagents() {
        CountingAgent agent = new CountingAgent();
        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(agent)
                .maxIterations(0)
                .outputKey("story")
                .build();

        Object result = loop.invoke(Map.of("topic", "dragons"));

        assertThat(agent.invocations).hasValue(0);
        assertThat(result).isNull();
    }

    @Test
    void negative_max_iterations_does_not_invoke_the_subagents() {
        CountingAgent agent = new CountingAgent();
        UntypedAgent loop =
                AgenticServices.loopBuilder().subAgents(agent).maxIterations(-1).build();

        loop.invoke(Map.of("topic", "dragons"));

        assertThat(agent.invocations).hasValue(0);
    }

    @Test
    void one_max_iteration_invokes_the_subagents_once() {
        CountingAgent agent = new CountingAgent();
        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(agent)
                .maxIterations(1)
                .outputKey("story")
                .build();

        Object result = loop.invoke(Map.of("topic", "dragons"));

        assertThat(agent.invocations).hasValue(1);
        assertThat(result).isEqualTo("a story about dragons");
    }

    @Test
    void loop_without_subagents_completes_like_an_empty_sequence() {
        UntypedAgent loop = AgenticServices.loopBuilder().maxIterations(3).build();
        UntypedAgent sequence = AgenticServices.sequenceBuilder().build();

        assertThat(loop.invoke(Map.of("topic", "dragons"))).isNull();
        assertThat(sequence.invoke(Map.of("topic", "dragons"))).isNull();
    }
}
