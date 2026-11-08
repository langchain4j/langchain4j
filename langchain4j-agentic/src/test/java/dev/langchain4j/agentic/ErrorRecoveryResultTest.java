package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ErrorRecoveryResultTest {

    static final ChatModel STUB_MODEL = new ChatModel() {
        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("draft story"))
                    .build();
        }
    };

    public interface StoryWriter {

        @UserMessage("Write a story about {{topic}}")
        @Agent(outputKey = "story")
        String write(@V("topic") String topic);
    }

    public interface StoryEditor {

        @UserMessage("Rewrite {{story}} in the style {{style}}")
        @Agent(outputKey = "story")
        String edit(@V("story") String story, @V("style") String style);
    }

    public interface StoryPublisher {

        @UserMessage("Publish {{story}}")
        @Agent(outputKey = "published")
        String publish(@V("story") String story);
    }

    @Test
    void error_handler_returning_result_completes_sequence() {
        StoryWriter writer = AgenticServices.agentBuilder(StoryWriter.class)
                .chatModel(STUB_MODEL)
                .build();
        StoryEditor editor = AgenticServices.agentBuilder(StoryEditor.class)
                .chatModel(STUB_MODEL)
                .build();
        StoryPublisher publisher = spy(AgenticServices.agentBuilder(StoryPublisher.class)
                .chatModel(STUB_MODEL)
                .build());

        AtomicReference<String> failedAgent = new AtomicReference<>();

        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(writer, editor, publisher)
                .outputKey("published")
                .errorHandler(errorContext -> {
                    failedAgent.set(errorContext.agentName());
                    return ErrorRecoveryResult.result("default story");
                })
                .build();

        Object result =
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> workflow.invoke(Map.of("topic", "dragons")));

        assertThat(failedAgent.get()).isEqualTo("edit");
        verify(publisher).publish("default story");
        assertThat(result).isEqualTo("draft story");
    }

    @Test
    void error_handler_returning_result_on_last_agent_returns_recovered_value() {
        StoryWriter writer = AgenticServices.agentBuilder(StoryWriter.class)
                .chatModel(STUB_MODEL)
                .build();
        StoryEditor editor = AgenticServices.agentBuilder(StoryEditor.class)
                .chatModel(STUB_MODEL)
                .build();

        AtomicReference<String> failedAgent = new AtomicReference<>();

        UntypedAgent workflow = AgenticServices.sequenceBuilder()
                .subAgents(writer, editor)
                .outputKey("story")
                .errorHandler(errorContext -> {
                    failedAgent.set(errorContext.agentName());
                    return ErrorRecoveryResult.result("default story");
                })
                .build();

        Object result =
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> workflow.invoke(Map.of("topic", "dragons")));

        assertThat(failedAgent.get()).isEqualTo("edit");
        assertThat(result).isEqualTo("default story");
    }
}
