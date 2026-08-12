package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentProxyEqualsTest {

    static final ChatModel DUMMY_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
        }
    };

    public interface Writer {

        @UserMessage("Write about {{topic}}")
        @Agent(outputKey = "story")
        String write(@V("topic") String topic);
    }

    private static Writer writerAgent() {
        return AgenticServices.agentBuilder(Writer.class)
                .chatModel(DUMMY_MODEL)
                .outputKey("story")
                .build();
    }

    private static UntypedAgent sequenceAgent() {
        return AgenticServices.sequenceBuilder()
                .subAgents(writerAgent())
                .outputKey("story")
                .build();
    }

    @Test
    void agent_is_equal_to_itself() {
        Writer agent = writerAgent();

        assertThat(agent.equals(agent)).isTrue();
    }

    @Test
    void distinct_agents_are_not_equal() {
        Writer agent = writerAgent();
        Writer otherAgent = writerAgent();

        assertThat(agent.equals(otherAgent)).isFalse();
        assertThat(agent.equals(null)).isFalse();
        assertThat(agent.equals("not an agent")).isFalse();
    }

    @Test
    void agent_can_be_looked_up_in_a_collection() {
        Writer agent = writerAgent();
        Writer otherAgent = writerAgent();
        List<Writer> agents = new ArrayList<>(List.of(agent));

        assertThat(agents.contains(agent)).isTrue();
        assertThat(agents.contains(otherAgent)).isFalse();
        assertThat(agents.remove(agent)).isTrue();
        assertThat(agents).isEmpty();
    }

    @Test
    void workflow_agent_is_equal_to_itself() {
        UntypedAgent agent = sequenceAgent();

        assertThat(agent.equals(agent)).isTrue();
    }

    @Test
    void distinct_workflow_agents_are_not_equal() {
        UntypedAgent agent = sequenceAgent();
        UntypedAgent otherAgent = sequenceAgent();

        assertThat(agent.equals(otherAgent)).isFalse();
        assertThat(agent.equals(null)).isFalse();
    }

    @Test
    void workflow_agent_can_be_looked_up_in_a_collection() {
        UntypedAgent agent = sequenceAgent();
        UntypedAgent otherAgent = sequenceAgent();
        List<UntypedAgent> agents = new ArrayList<>(List.of(agent));

        assertThat(agents.contains(agent)).isTrue();
        assertThat(agents.contains(otherAgent)).isFalse();
        assertThat(agents.remove(agent)).isTrue();
        assertThat(agents).isEmpty();
    }
}
