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
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class ParallelAgentExceptionParityTest {

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

    public interface Reviewer {

        @UserMessage("Review {{topic}}")
        @Agent(outputKey = "review")
        String review(@V("topic") String topic);
    }

    private UntypedAgent sequentialWorkflow() {
        return AgenticServices.sequenceBuilder()
                .subAgents(writer(), reviewer())
                .outputKey("story")
                .build();
    }

    private UntypedAgent parallelWorkflow() {
        return AgenticServices.parallelBuilder()
                .subAgents(writer(), reviewer())
                .outputKey("story")
                .build();
    }

    private Writer writer() {
        return AgenticServices.agentBuilder(Writer.class)
                .chatModel(FAILING_MODEL)
                .outputKey("story")
                .build();
    }

    private Reviewer reviewer() {
        return AgenticServices.agentBuilder(Reviewer.class)
                .chatModel(FAILING_MODEL)
                .outputKey("review")
                .build();
    }

    @Test
    void sequential_agent_failure_throws_agentInvocationException() {
        assertThatThrownBy(() -> sequentialWorkflow().invoke(Map.of("topic", "dragons")))
                .isInstanceOf(AgentInvocationException.class);
    }

    @Test
    void parallel_agent_failure_throws_same_type_as_sequential() {
        assertThatThrownBy(() -> parallelWorkflow().invoke(Map.of("topic", "dragons")))
                .isInstanceOf(AgentInvocationException.class)
                .isNotInstanceOf(ExecutionException.class)
                .isNotInstanceOf(CompletionException.class);
    }
}
