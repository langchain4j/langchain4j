package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.IllegalConfigurationException;
import org.junit.jupiter.api.Test;

class ContextTest {

    private static class RecordingChatModel implements ChatModel {

        private final String summary;
        private int invocations;

        RecordingChatModel(String summary) {
            this.summary = summary;
        }

        int invocations() {
            return invocations;
        }

        @Override
        public ChatResponse chat(ChatRequest chatRequest) {
            invocations++;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"summary\": \"" + summary + "\"}"))
                    .build();
        }
    }

    @Test
    void summarizers_using_different_models_should_each_use_their_own_model() {
        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.contextAsConversation("expert")).thenReturn("expert conversation");

        RecordingChatModel firstModel = new RecordingChatModel("summary-from-first-model");
        RecordingChatModel secondModel = new RecordingChatModel("summary-from-second-model");

        String firstTransformedMessage = new Context.Summarizer(agenticScope, firstModel, "expert")
                .transformUserMessage("first question", "memory");
        assertThat(firstTransformedMessage).startsWith("Considering this context \"");
        assertThat(firstTransformedMessage).contains("summary-from-first-model");
        assertThat(firstModel.invocations()).isEqualTo(1);

        String secondTransformedMessage = new Context.Summarizer(agenticScope, secondModel, "expert")
                .transformUserMessage("second question", "memory");
        assertThat(secondTransformedMessage).contains("summary-from-second-model");
        assertThat(secondModel.invocations()).isEqualTo(1);
        assertThat(firstModel.invocations()).isEqualTo(1);
    }

    @Test
    void summarizer_should_not_invoke_model_when_context_is_blank() {
        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.contextAsConversation("expert")).thenReturn("");
        RecordingChatModel model = new RecordingChatModel("unused");

        String transformedMessage =
                new Context.Summarizer(agenticScope, model, "expert").transformUserMessage("question", "memory");

        assertThat(transformedMessage).isEqualTo("question");
        assertThat(model.invocations()).isZero();
    }

    @Test
    void summarizer_should_fail_fast_when_model_is_missing() {
        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> new Context.Summarizer(mock(AgenticScope.class), null, "expert"));
    }
}
