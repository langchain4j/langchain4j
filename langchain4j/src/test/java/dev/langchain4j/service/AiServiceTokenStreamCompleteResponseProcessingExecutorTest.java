package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolServiceContext;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiServiceTokenStreamCompleteResponseProcessingExecutorTest {

    private static final String EXECUTOR_THREAD_NAME = "complete-response-processing-executor";

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, EXECUTOR_THREAD_NAME));

    private final ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
            .id("1")
            .name("currentTemperature")
            .arguments("{}")
            .build();

    static class Tools {

        final List<String> threadNames = new CopyOnWriteArrayList<>();

        @Tool
        String currentTemperature() {
            threadNames.add(Thread.currentThread().getName());
            return "42";
        }
    }

    static class ThreadRecordingChatMemoryStore extends InMemoryChatMemoryStore {

        final List<String> threadNames = new CopyOnWriteArrayList<>();

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            threadNames.add(Thread.currentThread().getName());
            super.updateMessages(memoryId, messages);
        }
    }

    interface Assistant {

        TokenStream chat(String message);
    }

    @AfterEach
    void shutdownExecutor() {
        executorService.shutdownNow();
    }

    @Test
    void should_process_every_complete_response_on_executor() throws Exception {

        // given
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(toolExecutionRequest), AiMessage.from("It is 42 degrees"));
        Tools tools = new Tools();
        AtomicInteger executedTasks = new AtomicInteger();
        Executor executor = task -> {
            executedTasks.incrementAndGet();
            executorService.execute(task);
        };
        ThreadRecordingChatMemoryStore chatMemoryStore = new ThreadRecordingChatMemoryStore();
        TokenStream tokenStream = tokenStream(model, tools, executor, null, chatMemoryStore);

        CompletableFuture<String> completeResponseThreadName = new CompletableFuture<>();
        CompletableFuture<ChatResponse> completeResponse = new CompletableFuture<>();

        // when
        tokenStream
                .onCompleteResponse(response -> {
                    completeResponseThreadName.complete(Thread.currentThread().getName());
                    completeResponse.complete(response);
                })
                .onError(completeResponse::completeExceptionally)
                .start();

        // then
        assertThat(completeResponse.get(10, SECONDS).aiMessage().text()).isEqualTo("It is 42 degrees");
        assertThat(tools.threadNames).containsExactly(EXECUTOR_THREAD_NAME);
        assertThat(chatMemoryStore.threadNames).isNotEmpty().containsOnly(EXECUTOR_THREAD_NAME);
        assertThat(completeResponseThreadName.get()).isEqualTo(EXECUTOR_THREAD_NAME);
        assertThat(executedTasks).hasValue(2); // one per model response
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void should_process_complete_response_on_delivering_thread_when_executor_runs_task_inline() throws Exception {

        // given
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(toolExecutionRequest), AiMessage.from("It is 42 degrees"));
        Tools tools = new Tools();
        List<String> deliveringThreadNames = new CopyOnWriteArrayList<>();
        Executor inlineExecutor = task -> {
            deliveringThreadNames.add(Thread.currentThread().getName());
            task.run();
        };
        TokenStream tokenStream = tokenStream(model, tools, inlineExecutor);

        CompletableFuture<ChatResponse> completeResponse = new CompletableFuture<>();

        // when
        tokenStream
                .onCompleteResponse(completeResponse::complete)
                .onError(completeResponse::completeExceptionally)
                .start();

        // then
        assertThat(completeResponse.get(10, SECONDS).aiMessage().text()).isEqualTo("It is 42 degrees");
        assertThat(deliveringThreadNames).hasSize(2); // one per model response
        assertThat(tools.threadNames).containsExactly(deliveringThreadNames.get(0));
    }

    @Test
    void should_wait_for_concurrently_executed_tools_on_executor() throws Exception {

        // given
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(toolExecutionRequest), AiMessage.from("It is 42 degrees"));
        Tools tools = new Tools();
        ExecutorService toolExecutorService =
                Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "tool-executor"));
        TokenStream tokenStream = tokenStream(model, tools, executorService, toolExecutorService);

        CompletableFuture<String> completeResponseThreadName = new CompletableFuture<>();
        CompletableFuture<ChatResponse> completeResponse = new CompletableFuture<>();

        // when
        try {
            tokenStream
                    .onCompleteResponse(response -> {
                        completeResponseThreadName.complete(Thread.currentThread().getName());
                        completeResponse.complete(response);
                    })
                    .onError(completeResponse::completeExceptionally)
                    .start();

            // then
            assertThat(completeResponse.get(10, SECONDS).aiMessage().text()).isEqualTo("It is 42 degrees");
        } finally {
            toolExecutorService.shutdownNow();
        }
        assertThat(tools.threadNames).containsExactly("tool-executor");
        assertThat(completeResponseThreadName.get()).isEqualTo(EXECUTOR_THREAD_NAME);
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void should_process_complete_response_on_delivering_thread_when_no_executor() throws Exception {

        // given
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(
                AiMessage.from(toolExecutionRequest), AiMessage.from("It is 42 degrees"));
        Tools tools = new Tools();
        TokenStream tokenStream = tokenStream(model, tools, null);

        CompletableFuture<ChatResponse> completeResponse = new CompletableFuture<>();

        // when
        tokenStream
                .onCompleteResponse(completeResponse::complete)
                .onError(completeResponse::completeExceptionally)
                .start();

        // then
        assertThat(completeResponse.get(10, SECONDS).aiMessage().text()).isEqualTo("It is 42 degrees");
        assertThat(tools.threadNames).singleElement().isNotEqualTo(EXECUTOR_THREAD_NAME);
    }

    @Test
    void should_pass_exception_thrown_on_executor_to_error_handler() throws Exception {

        // given
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("It is 42 degrees"));
        TokenStream tokenStream = tokenStream(model, new Tools(), executorService);

        RuntimeException exception = new RuntimeException("complete response handler failed");
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        // when
        tokenStream
                .onCompleteResponse(response -> {
                    throw exception;
                })
                .onError(error::complete)
                .start();

        // then
        assertThat(error.get(10, SECONDS)).isSameAs(exception);
    }

    @Test
    void should_pass_error_thrown_on_executor_to_error_handler() throws Exception {

        // given
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("It is 42 degrees"));
        TokenStream tokenStream = tokenStream(model, new Tools(), executorService);

        AssertionError assertionError = new AssertionError("complete response handler failed");
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        // when
        tokenStream
                .onCompleteResponse(response -> {
                    throw assertionError;
                })
                .onError(error::complete)
                .start();

        // then
        assertThat(error.get(10, SECONDS)).isSameAs(assertionError);
    }

    @Test
    void should_pass_exception_thrown_by_executor_to_error_handler() throws Exception {

        // given
        StreamingChatModelMock model = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("It is 42 degrees"));
        RejectedExecutionException exception = new RejectedExecutionException("rejected");
        TokenStream tokenStream = tokenStream(model, new Tools(), task -> {
            throw exception;
        });

        CompletableFuture<ChatResponse> completeResponse = new CompletableFuture<>();
        CompletableFuture<Throwable> error = new CompletableFuture<>();

        // when
        tokenStream
                .onCompleteResponse(completeResponse::complete)
                .onError(error::complete)
                .start();

        // then
        assertThat(error.get(10, SECONDS)).isSameAs(exception);
        assertThat(completeResponse).isNotDone();
    }

    private static TokenStream tokenStream(StreamingChatModelMock model, Tools tools, Executor executor) {
        return tokenStream(model, tools, executor, null);
    }

    private static TokenStream tokenStream(
            StreamingChatModelMock model, Tools tools, Executor executor, Executor toolExecutor) {
        return tokenStream(model, tools, executor, toolExecutor, null);
    }

    private static TokenStream tokenStream(
            StreamingChatModelMock model,
            Tools tools,
            Executor executor,
            Executor toolExecutor,
            ChatMemoryStore chatMemoryStore) {
        AiServiceContext context = AiServiceContext.create(Assistant.class);
        context.streamingChatModel = model;
        context.toolService.tools(List.of(tools));
        if (chatMemoryStore != null) {
            context.initChatMemories(memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(10)
                    .chatMemoryStore(chatMemoryStore)
                    .build());
        }

        UserMessage userMessage = UserMessage.from("What is the temperature?");
        List<ChatMessage> messages = List.of(userMessage);
        InvocationContext invocationContext = InvocationContext.builder()
                .interfaceName(Assistant.class.getName())
                .methodName("chat")
                .chatMemoryId("default")
                .userMessage(userMessage)
                .build();
        ToolServiceContext toolServiceContext =
                context.toolService.createContext(invocationContext, userMessage, messages);

        return new AiServiceTokenStream(AiServiceTokenStreamParameters.builder()
                .messages(messages)
                .toolServiceContext(toolServiceContext)
                .toolArgumentsErrorHandler((e, c) -> {
                    throw new RuntimeException(e);
                })
                .toolExecutionErrorHandler((e, c) -> ToolErrorHandlerResult.text(e.getMessage()))
                .toolExecutor(toolExecutor)
                .completeResponseProcessingExecutor(executor)
                .context(context)
                .invocationContext(invocationContext)
                .build());
    }
}
