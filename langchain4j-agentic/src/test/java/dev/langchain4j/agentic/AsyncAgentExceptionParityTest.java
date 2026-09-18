package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class AsyncAgentExceptionParityTest {

    static final ChatModel FAILING_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            throw new RuntimeException("model failure");
        }
    };

    public interface Writer {

        @UserMessage("Write about {{topic}}")
        @Agent(outputKey = "story")
        String write(@V("topic") String topic);
    }

    private UntypedAgent workflowWith(boolean async) {
        Writer writer = AgenticServices.agentBuilder(Writer.class)
                .chatModel(FAILING_MODEL)
                .async(async)
                .outputKey("story")
                .build();
        return AgenticServices.sequenceBuilder()
                .subAgents(writer)
                .outputKey("story")
                .build();
    }

    @Test
    void sync_agent_failure_throws_agentInvocationException() {
        assertThatThrownBy(() -> workflowWith(false).invoke(Map.of("topic", "dragons")))
                .isInstanceOf(AgentInvocationException.class);
    }

    @Test
    void async_agent_failure_throws_same_type_as_sync() {
        assertThatThrownBy(() -> workflowWith(true).invoke(Map.of("topic", "dragons")))
                .isInstanceOf(AgentInvocationException.class)
                .isNotInstanceOf(CompletionException.class);
    }
}
