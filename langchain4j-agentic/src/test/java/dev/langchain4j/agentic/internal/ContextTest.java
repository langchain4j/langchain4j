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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    @Test
    void summarizers_sharing_the_same_model_should_share_a_single_service() {
        RecordingChatModel model = new RecordingChatModel("shared summary");

        Context.ContextSummarizer first = Context.createSummarizer(model);
        Context.ContextSummarizer second = Context.createSummarizer(model);

        assertThat(second).isSameAs(first);
    }

    @Test
    void summarizers_with_different_models_should_not_share_a_service() {
        Context.ContextSummarizer first = Context.createSummarizer(new RecordingChatModel("first summary"));
        Context.ContextSummarizer second = Context.createSummarizer(new RecordingChatModel("second summary"));

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void concurrent_createSummarizer_calls_with_the_same_model_should_share_a_single_service() throws Exception {
        RecordingChatModel model = new RecordingChatModel("concurrent summary");
        int threads = 8;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Context.ContextSummarizer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return Context.createSummarizer(model);
                }));
            }
            Context.ContextSummarizer first = futures.get(0).get();
            for (Future<Context.ContextSummarizer> future : futures) {
                assertThat(future.get()).isSameAs(first);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
