package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ParallelAgentExceptionParityTest {

    static final Map<String, Object> INPUT = Map.of("topic", "dragons");

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

    private static ChatModel countingModel(AtomicInteger counter) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest chatRequest) {
                counter.incrementAndGet();
                return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
            }
        };
    }

    private static Writer writer(ChatModel model) {
        return AgenticServices.agentBuilder(Writer.class)
                .chatModel(model)
                .outputKey("story")
                .build();
    }

    private static Reviewer reviewer(ChatModel model) {
        return AgenticServices.agentBuilder(Reviewer.class)
                .chatModel(model)
                .outputKey("review")
                .build();
    }

    private static UntypedAgent sequentialWorkflow(ChatModel model) {
        return AgenticServices.sequenceBuilder()
                .subAgents(writer(model), reviewer(model))
                .build();
    }

    private static UntypedAgent parallelWorkflow(ChatModel model) {
        return AgenticServices.parallelBuilder()
                .subAgents(writer(model), reviewer(model))
                .build();
    }

    private static Throwable failureOf(UntypedAgent workflow) {
        return catchThrowable(() -> workflow.invoke(INPUT));
    }

    @Test
    void sequential_agent_failure_throws_agentInvocationException() {
        assertThat(failureOf(sequentialWorkflow(FAILING_MODEL))).isExactlyInstanceOf(AgentInvocationException.class);
    }

    @Test
    void parallel_agent_failure_throws_same_type_as_sequential() {
        assertThat(failureOf(parallelWorkflow(FAILING_MODEL)))
                .isExactlyInstanceOf(AgentInvocationException.class)
                .hasSameClassAs(failureOf(sequentialWorkflow(FAILING_MODEL)));
    }

    @Test
    void parallel_agents_without_failure_are_all_invoked() {
        AtomicInteger invocations = new AtomicInteger();

        assertThatCode(() -> parallelWorkflow(countingModel(invocations)).invoke(INPUT))
                .doesNotThrowAnyException();
        assertThat(invocations).hasValue(2);
    }
}
