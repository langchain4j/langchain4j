package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SequentialAgentInputValidationTest {

    public interface Writer {

        @UserMessage("Write about {{topic}}")
        @Agent
        String write(@V("topic") String topic);
    }

    public interface Editor {

        @UserMessage("Edit {{story}} for {{audience}}")
        @Agent
        String edit(@V("story") String story, @V("audience") String audience);
    }

    @Test
    void missing_later_input_fails_before_the_first_agent_runs() {
        AtomicInteger modelCalls = new AtomicInteger();
        UntypedAgent workflow = workflow("storyOut1", modelCalls);

        assertThatThrownBy(() -> workflow.invoke(Map.of("topic", "dragons", "audience", "adults")))
                .isInstanceOf(MissingArgumentException.class)
                .hasMessageContaining("story");
        assertThat(modelCalls).hasValue(0);
    }

    @Test
    void declared_output_satisfies_later_input() {
        AtomicInteger modelCalls = new AtomicInteger();
        UntypedAgent workflow = workflow("story", modelCalls);

        workflow.invoke(Map.of("topic", "dragons", "audience", "adults"));

        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void invocation_input_can_satisfy_later_input() {
        AtomicInteger modelCalls = new AtomicInteger();
        UntypedAgent workflow = workflow("storyOut1", modelCalls);

        workflow.invoke(Map.of("topic", "dragons", "audience", "adults", "story", "existing"));

        assertThat(modelCalls).hasValue(2);
    }

    @Test
    void dynamic_scope_write_by_preceding_action_satisfies_later_input() {
        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                modelCalls.incrementAndGet();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("edited result"))
                        .build();
            }
        };
        AgenticServices.AgenticScopeAction writer = AgenticServices.agentAction(scope -> {
            scope.writeState("story", "dynamically provided story");
        });
        Editor editor = AgenticServices.agentBuilder(Editor.class)
                .chatModel(model)
                .outputKey("edited")
                .build();
        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(writer, editor)
                .outputKey("edited")
                .build();

        workflow.invoke(Map.of("topic", "dragons", "audience", "adults"));
        assertThat(modelCalls).hasValue(1); // Editor was called
    }

    private static UntypedAgent workflow(String writerOutputKey, AtomicInteger modelCalls) {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                modelCalls.incrementAndGet();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("result"))
                        .build();
            }
        };
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .chatModel(model)
                .outputKey(writerOutputKey)
                .build();
        Editor editor = AgenticServices.agentBuilder(Editor.class)
                .chatModel(model)
                .outputKey("edited")
                .build();
        return AgenticServices.sequenceBuilder()
                .subAgents(writer, editor)
                .outputKey("edited")
                .build();
    }
}
