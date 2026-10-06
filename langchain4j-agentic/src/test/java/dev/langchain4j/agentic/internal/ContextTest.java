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
import java.util.concurrent.atomic.AtomicReference;
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

    private static class EqualRecordingChatModel extends RecordingChatModel {

        EqualRecordingChatModel(String summary) {
            super(summary);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualRecordingChatModel;
        }

        @Override
        public int hashCode() {
            return EqualRecordingChatModel.class.hashCode();
        }
    }

    @Test
    void summarizers_using_different_models_should_each_use_their_own_model() {
        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.contextAsConversation("expert")).thenReturn("expert conversation");

        RecordingChatModel firstModel = new EqualRecordingChatModel("summary-from-first-model");
        RecordingChatModel secondModel = new EqualRecordingChatModel("summary-from-second-model");

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
    void summarizers_created_for_the_same_model_should_be_independent() {
        RecordingChatModel model = new RecordingChatModel("shared summary");

        Context.ContextSummarizer first = Context.createSummarizer(model);
        Context.ContextSummarizer second = Context.createSummarizer(model);

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void summarizers_with_different_models_should_not_share_a_service() {
        Context.ContextSummarizer first = Context.createSummarizer(new RecordingChatModel("first summary"));
        Context.ContextSummarizer second = Context.createSummarizer(new RecordingChatModel("second summary"));

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void concurrent_createSummarizer_calls_with_the_same_model_should_create_independent_services() throws Exception {
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
            for (Future<Context.ContextSummarizer> future : futures) {
                assertThat(future.get()).isNotNull();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void summarizer_with_chat_model_provider_should_follow_model_changes() {
        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.contextAsConversation("expert")).thenReturn("expert conversation");

        RecordingChatModel firstModel = new EqualRecordingChatModel("summary-from-first-model");
        RecordingChatModel secondModel = new EqualRecordingChatModel("summary-from-second-model");
        AtomicReference<ChatModel> currentModel = new AtomicReference<>(firstModel);

        Context.Summarizer summarizer =
                Context.Summarizer.withChatModelProvider(agenticScope, ignored -> currentModel.get(), "expert");

        assertThat(summarizer.transformUserMessage("question", "memory")).contains("summary-from-first-model");
        currentModel.set(secondModel);
        assertThat(summarizer.transformUserMessage("question", "memory")).contains("summary-from-second-model");
        currentModel.set(firstModel);
        assertThat(summarizer.transformUserMessage("question", "memory")).contains("summary-from-first-model");

        assertThat(firstModel.invocations()).isEqualTo(2);
        assertThat(secondModel.invocations()).isEqualTo(1);
    }

    @Test
    void summarizer_with_chat_model_provider_should_fail_when_provider_returns_null() {
        AgenticScope agenticScope = mock(AgenticScope.class);
        when(agenticScope.contextAsConversation("expert")).thenReturn("expert conversation");

        Context.Summarizer summarizer =
                Context.Summarizer.withChatModelProvider(agenticScope, ignored -> null, "expert");

        assertThatExceptionOfType(IllegalConfigurationException.class)
                .isThrownBy(() -> summarizer.transformUserMessage("question", "memory"))
                .withMessage("The chat model provider returned null while summarizing agentic context.");
    }
}
