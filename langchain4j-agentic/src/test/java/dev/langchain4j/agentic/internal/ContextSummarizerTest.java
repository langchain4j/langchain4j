package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextSummarizerTest {

    @Test
    void should_summarize_with_the_chat_model_of_each_summarizer_instance() {
        RecordingChatModel firstModel = new RecordingChatModel("summary from the first model");
        RecordingChatModel secondModel = new RecordingChatModel("summary from the second model");

        Context.Summarizer firstSummarizer = new Context.Summarizer(agenticScope(), firstModel, "expert");
        Context.Summarizer secondSummarizer = new Context.Summarizer(agenticScope(), secondModel, "expert");

        String firstContext = firstSummarizer.transformUserMessage("user question", "memory-id");
        String secondContext = secondSummarizer.transformUserMessage("user question", "memory-id");

        assertThat(firstContext).contains("summary from the first model");
        assertThat(secondContext).contains("summary from the second model");
        assertThat(firstModel.requests).hasSize(1);
        assertThat(secondModel.requests).hasSize(1);
    }

    @Test
    void should_reuse_summarizer_for_the_same_chat_model() {
        RecordingChatModel model = new RecordingChatModel("the summary");

        Context.Summarizer firstSummarizer = new Context.Summarizer(agenticScope(), model, "expert");
        Context.Summarizer secondSummarizer = new Context.Summarizer(agenticScope(), model, "expert");

        firstSummarizer.transformUserMessage("user question", "memory-id");
        secondSummarizer.transformUserMessage("user question", "memory-id");

        assertThat(model.requests).hasSize(2);
    }

    private static AgenticScope agenticScope() {
        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.contextAsConversation(new String[] {"expert"}))
                .thenReturn("user: what is the weather?\nexpert: it is sunny in Berlin");
        return agenticScope;
    }

    static class RecordingChatModel implements ChatModel {

        private final String summary;
        final List<ChatRequest> requests = new ArrayList<>();

        RecordingChatModel(String summary) {
            this.summary = summary;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"summary\": \"" + summary + "\"}"))
                    .build();
        }
    }
}
