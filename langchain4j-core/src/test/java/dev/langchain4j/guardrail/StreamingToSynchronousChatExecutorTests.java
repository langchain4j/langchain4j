package dev.langchain4j.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StreamingToSynchronousChatExecutorTests {

    private static final InvocationContext INVOCATION_CONTEXT = InvocationContext.builder()
            .interfaceName("TestInterface")
            .methodName("testMethod")
            .chatMemoryId("test-memory")
            .build();

    private static final ChatRequest CHAT_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hello")).build();

    @Test
    void execute_returnsResponse_whenStreamCompletesNormally() {
        // given
        StreamingChatModel streamingChatModel = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("Hello"));
        ChatExecutor executor = ChatExecutor.builder(streamingChatModel)
                .chatRequest(CHAT_REQUEST)
                .invocationContext(INVOCATION_CONTEXT)
                .build();

        // when
        ChatResponse response = executor.execute();

        // then
        assertThat(response.aiMessage().text()).isEqualTo("Hello");
    }

    @Test
    void execute_propagatesErrorAndDeliversItToErrorHandler_whenStreamFails() {
        // given
        StreamingChatModel streamingChatModel = StreamingChatModelMock.thatAlwaysThrowsException();
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        ChatExecutor executor = ChatExecutor.builder(streamingChatModel)
                .errorHandler(capturedError::set)
                .chatRequest(CHAT_REQUEST)
                .invocationContext(INVOCATION_CONTEXT)
                .build();

        // when
        Throwable thrown = executeWithTimeout(executor);

        // then
        assertThat(thrown)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Something went wrong");
        assertThat(capturedError.get()).isSameAs(thrown.getCause());
    }

    @Test
    void execute_doesNotBlock_whenErrorArrivesOnBackgroundThread() {
        // given
        RuntimeException expectedError = new RuntimeException("async failure");
        StreamingChatModel streamingChatModel = modelThatFailsOnBackgroundThread(expectedError);
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        ChatExecutor executor = ChatExecutor.builder(streamingChatModel)
                .errorHandler(capturedError::set)
                .chatRequest(CHAT_REQUEST)
                .invocationContext(INVOCATION_CONTEXT)
                .build();

        // when
        Throwable thrown = executeWithTimeout(executor);

        // then: execute() must not block forever even though only onError (not onCompleteResponse) is called
        assertThat(thrown).isInstanceOf(ExecutionException.class);
        assertThat(thrown.getCause()).isSameAs(expectedError);
        assertThat(capturedError.get()).isSameAs(expectedError);
    }

    @Test
    void execute_propagatesError_whenNoErrorHandlerConfigured() {
        // given
        StreamingChatModel streamingChatModel = StreamingChatModelMock.thatAlwaysThrowsException();
        ChatExecutor executor = ChatExecutor.builder(streamingChatModel)
                .chatRequest(CHAT_REQUEST)
                .invocationContext(INVOCATION_CONTEXT)
                .build();

        // when
        Throwable thrown = executeWithTimeout(executor);

        // then
        assertThat(thrown).isInstanceOf(ExecutionException.class);
        assertThat(thrown.getCause()).isInstanceOf(RuntimeException.class);
    }

    private static Throwable executeWithTimeout(ChatExecutor executor) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<ChatResponse> future = executorService.submit((Callable<ChatResponse>) executor::execute);
            return catchThrowable(() -> future.get(5, TimeUnit.SECONDS));
        } finally {
            executorService.shutdownNow();
        }
    }

    private static StreamingChatModel modelThatFailsOnBackgroundThread(RuntimeException error) {
        return new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                Thread thread = new Thread(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    handler.onError(error);
                });
                thread.setDaemon(true);
                thread.start();
            }
        };
    }
}
