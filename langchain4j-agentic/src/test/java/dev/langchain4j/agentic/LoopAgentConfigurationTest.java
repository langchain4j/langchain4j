package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.declarative.LoopAgent;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LoopAgentConfigurationTest {

    static final ChatModel DUMMY_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("test-response"))
                    .build();
        }
    };

    public static class CountingAgent {

        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Agent(outputKey = "story")
        public String write(@V("topic") String topic) {
            INVOCATIONS.incrementAndGet();
            return "a story about " + topic;
        }
    }

    public interface StoryAgent {

        @UserMessage("Write a story about {{topic}}")
        @Agent(outputKey = "story")
        String write(@V("topic") String topic);
    }

    public interface ZeroIterationsLoop {

        @LoopAgent(
                outputKey = "story",
                maxIterations = 0,
                subAgents = {StoryAgent.class})
        String write(@V("topic") String topic);
    }

    @Test
    void build_fails_when_max_iterations_is_zero() {
        assertThatThrownBy(() -> AgenticServices.loopBuilder()
                        .subAgents(new CountingAgent())
                        .maxIterations(0)
                        .build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("maxIterations")
                .hasMessageContaining("0");
    }

    @Test
    void build_fails_when_max_iterations_is_negative() {
        assertThatThrownBy(() -> AgenticServices.loopBuilder()
                        .subAgents(new CountingAgent())
                        .maxIterations(-1)
                        .build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("maxIterations")
                .hasMessageContaining("-1");
    }

    @Test
    void build_fails_when_declarative_loop_has_zero_max_iterations() {
        assertThatThrownBy(() -> AgenticServices.createAgenticSystem(ZeroIterationsLoop.class, DUMMY_MODEL))
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("maxIterations");
    }

    @Test
    void build_fails_when_no_subagent() {
        assertThatThrownBy(() -> AgenticServices.loopBuilder().maxIterations(3).build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("at least one subagent");
    }

    @Test
    void one_max_iteration_invokes_the_subagents_once() {
        CountingAgent.INVOCATIONS.set(0);
        UntypedAgent loop = AgenticServices.loopBuilder()
                .subAgents(new CountingAgent())
                .maxIterations(1)
                .outputKey("story")
                .build();

        Object result = loop.invoke(Map.of("topic", "dragons"));

        assertThat(CountingAgent.INVOCATIONS).hasValue(1);
        assertThat(result).isEqualTo("a story about dragons");
    }
}
