package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An optional agent that stores its result nowhere has no output key to read back when it is
 * skipped, and it must still be skippable.
 */
class OptionalAgentWithoutOutputKeyTest {

    static final AtomicInteger SIDE_EFFECT_INVOCATIONS = new AtomicInteger();

    static final ChatModel SIDE_EFFECT_MODEL = new ChatModel() {

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            SIDE_EFFECT_INVOCATIONS.incrementAndGet();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("side-effect-response"))
                    .build();
        }
    };

    static final ChatModel CLOSING_MODEL = new ChatModel() {

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("test-response"))
                    .build();
        }
    };

    public interface SideEffectAgent {

        @Agent(description = "An optional agent that is not expected to be needed")
        @UserMessage("Perform the side effect for {{requiredInput}}")
        String sideEffect(@V("requiredInput") String requiredInput);
    }

    public interface ClosingAgent {

        @Agent(description = "Closes the workflow", outputKey = "closing")
        @UserMessage("Close the workflow")
        String close();
    }

    @BeforeEach
    void resetInvocations() {
        SIDE_EFFECT_INVOCATIONS.set(0);
    }

    @Test
    void optional_agent_without_output_key_is_skipped_when_one_of_its_arguments_is_missing() {
        assertThat(workflow().invoke(Map.of())).isEqualTo("test-response");
        assertThat(SIDE_EFFECT_INVOCATIONS).hasValue(0);
    }

    @Test
    void optional_agent_without_output_key_runs_when_all_of_its_arguments_are_present() {
        assertThat(workflow().invoke(Map.of("requiredInput", "value"))).isEqualTo("test-response");
        assertThat(SIDE_EFFECT_INVOCATIONS).hasValue(1);
    }

    @Test
    void optional_agent_without_output_key_returns_null_when_it_is_the_last_agent() {
        SideEffectAgent sideEffectAgent = AgenticServices.agentBuilder(SideEffectAgent.class)
                .chatModel(SIDE_EFFECT_MODEL)
                .optional(true)
                .build();

        UntypedAgent singleAgentWorkflow = AgenticServices.sequenceBuilder()
                .subAgents(List.of(sideEffectAgent))
                .build();

        assertThat(singleAgentWorkflow.invoke(Map.of())).isNull();
        assertThat(SIDE_EFFECT_INVOCATIONS).hasValue(0);
    }

    private static UntypedAgent workflow() {
        SideEffectAgent sideEffectAgent = AgenticServices.agentBuilder(SideEffectAgent.class)
                .chatModel(SIDE_EFFECT_MODEL)
                .optional(true)
                .build();

        ClosingAgent closingAgent = AgenticServices.agentBuilder(ClosingAgent.class)
                .chatModel(CLOSING_MODEL)
                .outputKey("closing")
                .build();

        return AgenticServices.sequenceBuilder()
                .subAgents(List.of(sideEffectAgent, closingAgent))
                .outputKey("closing")
                .build();
    }
}
