package dev.langchain4j.guardrail;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChatExecutorInvocationScopeTest {

    private static final ChatRequest CHAT_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hello")).build();

    private final InvocationContext invocationContext =
            InvocationContext.builder().invocationId(UUID.randomUUID()).build();

    private final List<UUID> requestInvocationIds = new CopyOnWriteArrayList<>();

    private final ChatModelListener listener = new ChatModelListener() {
        @Override
        public void onRequest(ChatModelRequestContext requestContext) {
            requestInvocationIds.add(requestContext.invocationId());
        }
    };

    @Test
    void chat_executor_should_call_the_model_as_work_of_the_invocation() {
        ChatExecutor executor = ChatExecutor.builder(
                        MockChatModel.thatResponds("answer").thenRepeats().withListeners(listener))
                .chatRequest(CHAT_REQUEST)
                .invocationContext(invocationContext)
                .build();

        executor.execute();
        assertThat(executor.executeAsync()).succeedsWithin(5, SECONDS);

        assertThat(requestInvocationIds)
                .containsExactly(invocationContext.invocationId(), invocationContext.invocationId());
    }

    @Test
    void streaming_chat_executor_should_call_the_model_as_work_of_the_invocation() {
        ChatExecutor executor = ChatExecutor.builder(MockStreamingChatModel.thatStreams("answer").thenRepeats()
                        .withListeners(listener))
                .chatRequest(CHAT_REQUEST)
                .invocationContext(invocationContext)
                .build();

        executor.execute();
        assertThat(executor.executeAsync()).succeedsWithin(5, SECONDS);

        assertThat(requestInvocationIds)
                .containsExactly(invocationContext.invocationId(), invocationContext.invocationId());
    }

    @Test
    void streaming_chat_executor_should_call_the_error_handler_outside_of_the_invocation() {
        AtomicReference<UUID> currentInErrorHandler = new AtomicReference<>(UUID.randomUUID());
        ChatExecutor executor = ChatExecutor.builder(MockStreamingChatModel.thatFails(new IllegalStateException("failed")))
                .errorHandler(error -> currentInErrorHandler.set(CurrentInvocation.invocationId()))
                .chatRequest(CHAT_REQUEST)
                .invocationContext(invocationContext)
                .build();

        assertThat(executor.executeAsync()).failsWithin(5, SECONDS);

        assertThat(currentInErrorHandler.get()).isNull();
    }
}
