package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

class ContextSummarizerTest {

    @Test
    void each_summarizer_uses_its_own_chat_model() {
        RecordingChatModel firstModel = new RecordingChatModel("first summary");
        RecordingChatModel secondModel = new RecordingChatModel("second summary");

        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.contextAsConversation()).thenReturn("a conversation to summarize");

        Context.Summarizer firstSummarizer = new Context.Summarizer(agenticScope, firstModel);
        Context.Summarizer secondSummarizer = new Context.Summarizer(agenticScope, secondModel);

        assertThat(firstSummarizer.transformUserMessage("user message", "memoryId"))
                .contains("first summary");
        assertThat(secondSummarizer.transformUserMessage("user message", "memoryId"))
                .contains("second summary");

        assertThat(firstModel.invocations()).isEqualTo(1);
        assertThat(secondModel.invocations()).isEqualTo(1);
    }

    @Test
    void summarizer_is_not_created_when_there_is_no_context_to_summarize() {
        RecordingChatModel model = new RecordingChatModel("unused summary");

        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.contextAsConversation()).thenReturn("");

        Context.Summarizer summarizer = new Context.Summarizer(agenticScope, model);

        assertThat(summarizer.transformUserMessage("user message", "memoryId")).isEqualTo("user message");
        assertThat(model.invocations()).isZero();
    }

    static class RecordingChatModel implements ChatModel {

        private final String summary;
        private int invocations;

        RecordingChatModel(String summary) {
            this.summary = summary;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            invocations++;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"summary\": \"" + summary + "\"}"))
                    .build();
        }

        int invocations() {
            return invocations;
        }
    }
}
