package dev.langchain4j.service.tool;

import static dev.langchain4j.service.tool.ToolService.executeWithErrorHandling;
import static dev.langchain4j.service.tool.ToolService.refreshDynamicProviders;
import static dev.langchain4j.service.tool.ToolService.shouldReturnImmediately;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import dev.langchain4j.service.tool.ToolService.CompensableToolExecution;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ToolServiceTest {

    private static final ToolExecutionRequest DUMMY_REQUEST =
            ToolExecutionRequest.builder().name("test").arguments("{}").build();

    private static final InvocationContext DUMMY_CONTEXT =
            InvocationContext.builder().build();

    private static final ToolArgumentsErrorHandler DEFAULT_ARGS_HANDLER = (error, ctx) -> {
        if (error instanceof RuntimeException re) throw re;
        throw new RuntimeException(error);
    };

    private static final ToolExecutionErrorHandler DEFAULT_EXEC_HANDLER =
            (error, ctx) -> ToolErrorHandlerResult.text(error.getMessage());

    @Test
    void startTool_should_return_a_failed_future_when_the_executor_rejects_the_tool() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.shutdown();

        CompletableFuture<ToolExecutionResult> future =
                new ToolService().startTool(DUMMY_REQUEST, Map.of(), DUMMY_CONTEXT, null, null, executor);

        assertThat(future)
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingCause()
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void compensateIfNeededAsync_should_return_a_failed_future_when_collecting_the_tools_throws_an_error() {
        ToolService toolService = new ToolService();
        toolService.compensateOnToolErrors(true);
        AssertionError error = new AssertionError("broken list of compensable tools");

        CompletableFuture<Void> future = toolService.compensateIfNeededAsync(
                List.of(),
                Map.of(),
                List.of(),
                failingOnEveryCall(error),
                true,
                null,
                null,
                DUMMY_CONTEXT,
                AiServiceListenerRegistrar.newInstance(),
                null,
                true,
                "credit",
                null);

        assertThat(future).failsWithin(Duration.ofSeconds(1)).withThrowableThat().havingCause().isSameAs(error);
    }

    @Test
    void compensateOnCancellationAsync_should_return_a_failed_future_when_collecting_the_tools_throws_an_error() {
        ToolService toolService = new ToolService();
        toolService.compensateOnToolErrors(true);
        AssertionError error = new AssertionError("broken list of compensable tools");

        CompletableFuture<Void> future = toolService.compensateOnCancellationAsync(
                null, null, null, failingOnEveryCall(error), null, null, DUMMY_CONTEXT,
                AiServiceListenerRegistrar.newInstance());

        assertThat(future).failsWithin(Duration.ofSeconds(1)).withThrowableThat().havingCause().isSameAs(error);
    }

    @SuppressWarnings("unchecked")
    private static List<CompensableToolExecution> failingOnEveryCall(Error error) {
        return mock(List.class, invocation -> {
            throw error;
        });
    }

    @Test
    void shouldReturnImmediately_empty_list_should_not_crash() {
        assertThatNoException().isThrownBy(() -> shouldReturnImmediately(false, List.of()));
    }

    @SuppressWarnings("unused")
    public static class DateTool {

        @Tool("returns the current date")
        public String getCurrentDate() {
            return "2024-04-29";
        }
    }

    /**
     * Overrides the tool method without repeating the annotation and delegates to it,
     * just like the subclasses generated by class-based proxies (CGLIB, Byte Buddy).
     */
    public static class InterceptedDateTool extends DateTool {

        boolean intercepted;

        @Override
        public String getCurrentDate() {
            intercepted = true;
            return super.getCurrentDate();
        }
    }

    @Test
    void findTools_should_find_tool_annotated_on_overridden_method() {
        List<AiServiceTool> tools = ToolService.findTools(new InterceptedDateTool());

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).name()).isEqualTo("getCurrentDate");
        assertThat(tools.get(0).toolSpecification().description()).isEqualTo("returns the current date");
    }

    @Test
    void findTools_should_execute_tool_through_overriding_method() {
        InterceptedDateTool objectWithTools = new InterceptedDateTool();

        ToolExecutor toolExecutor = ToolService.findTools(objectWithTools).get(0).toolExecutor();
        String result = toolExecutor.execute(
                ToolExecutionRequest.builder().name("getCurrentDate").arguments("{}").build(), null);

        assertThat(result).isEqualTo("2024-04-29");
        assertThat(objectWithTools.intercepted).isTrue();
    }

    // --- error handler returns text ---

    @Test
    void text_factory_returns_error_result() {
        RuntimeException original = new RuntimeException("fail");
        ToolExecutor executor = (req, ctx) -> {
            throw original;
        };

        ToolExecutionResult result = executeWithErrorHandling(
                DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, DEFAULT_EXEC_HANDLER);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).isEqualTo("fail");
    }

    @Test
    void propagated_tool_execution_exception_is_unwrapped() {
        RuntimeException original = new RuntimeException("boom");
        ToolExecutor executor = (req, ctx) -> {
            throw new ToolExecutionException(original);
        };

        ToolExecutionErrorHandler handler = (error, ctx) -> {
            assertThat(error).isSameAs(original);
            assertThat(ctx.rawError())
                    .isInstanceOf(ToolExecutionException.class)
                    .hasCause(original);
            return ToolErrorHandlerResult.text(error.getMessage());
        };

        ToolExecutionResult result =
                executeWithErrorHandling(DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, handler);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).isEqualTo("boom");
    }

    // --- rawError tests ---

    @Test
    void rawError_available_in_context() {
        RuntimeException original = new RuntimeException("outer", new IllegalArgumentException("inner"));
        ToolExecutor executor = (req, ctx) -> {
            throw original;
        };

        ToolExecutionErrorHandler handler = (error, ctx) -> {
            assertThat(ctx.rawError()).isSameAs(original);
            assertThat(error).isSameAs(original.getCause());
            return ToolErrorHandlerResult.text("handled");
        };

        ToolExecutionResult result =
                executeWithErrorHandling(DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, handler);

        assertThat(result.isError()).isTrue();
    }

    @Test
    void rawError_null_when_not_set() {
        ToolErrorContext ctx = ToolErrorContext.builder()
                .toolExecutionRequest(DUMMY_REQUEST)
                .invocationContext(DUMMY_CONTEXT)
                .build();

        assertThat(ctx.rawError()).isNull();
    }

    @Test
    void rawError_differs_from_cause() {
        IllegalArgumentException cause = new IllegalArgumentException("bad arg");
        RuntimeException wrapper = new RuntimeException("wrapper", cause);
        ToolExecutor executor = (req, ctx) -> {
            throw wrapper;
        };

        ToolExecutionErrorHandler handler = (error, ctx) -> {
            assertThat(error).isSameAs(cause);
            assertThat(ctx.rawError()).isSameAs(wrapper);
            return ToolErrorHandlerResult.text("ok");
        };

        executeWithErrorHandling(DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, handler);
    }

    @Test
    void rawError_allows_handler_to_throw_directly() {
        RuntimeException original = new IllegalStateException("critical");
        ToolExecutor executor = (req, ctx) -> {
            throw original;
        };

        ToolExecutionErrorHandler handler = (error, ctx) -> {
            throw (RuntimeException) ctx.rawError();
        };

        assertThatThrownBy(() ->
                        executeWithErrorHandling(DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, handler))
                .isSameAs(original);
    }

    // --- callback getter tests ---

    @Test
    void beforeToolExecution_getter() {
        ToolService toolService = new ToolService();
        Consumer<BeforeToolExecution> callback = before -> {};
        toolService.beforeToolExecution(callback);
        assertThat(toolService.beforeToolExecution()).isSameAs(callback);
    }

    @Test
    void afterToolExecution_getter() {
        ToolService toolService = new ToolService();
        Consumer<ToolExecution> callback = after -> {};
        toolService.afterToolExecution(callback);
        assertThat(toolService.afterToolExecution()).isSameAs(callback);
    }

    @Test
    void callbacks_null_by_default() {
        ToolService toolService = new ToolService();
        assertThat(toolService.beforeToolExecution()).isNull();
        assertThat(toolService.afterToolExecution()).isNull();
    }

    // --- refreshDynamicProviders ---

    private static ToolServiceContext contextWith(ToolProvider dynamicProvider) {
        return ToolServiceContext.builder()
                .effectiveTools(new ArrayList<>())
                .availableTools(new ArrayList<>())
                .toolExecutors(new HashMap<>())
                .returnBehaviors(new HashMap<>())
                .dynamicToolProviders(List.of(dynamicProvider))
                .build();
    }

    @Test
    void refreshDynamicProviders_uses_invocation_user_message_when_evicted_from_messages() {
        List<ChatMessage> messages = List.of(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("previous")
                        .arguments("{}")
                        .build()),
                ToolExecutionResultMessage.from("id", "previous", "result"));

        UserMessage currentUserMessage = UserMessage.from("do the thing");
        InvocationContext invocationContext =
                InvocationContext.builder().userMessage(currentUserMessage).build();

        AtomicReference<UserMessage> seenByProvider = new AtomicReference<>();
        ToolProvider dynamicProvider = request -> {
            seenByProvider.set(request.userMessage());
            return ToolProviderResult.builder()
                    .add(ToolSpecification.builder().name("dynamic_tool").build(), (req, memoryId) -> "ok")
                    .build();
        };

        ToolServiceContext refreshed =
                refreshDynamicProviders(contextWith(dynamicProvider), messages, invocationContext);

        assertThat(seenByProvider.get()).isEqualTo(currentUserMessage);
        assertThat(refreshed.toolExecutors()).containsKey("dynamic_tool");
        assertThat(refreshed.effectiveTools())
                .extracting(ToolSpecification::name)
                .contains("dynamic_tool");
    }

    @Test
    void refreshDynamicProviders_returns_context_unchanged_when_no_user_message_available() {
        List<ChatMessage> messages = List.of(
                AiMessage.from(ToolExecutionRequest.builder()
                        .name("previous")
                        .arguments("{}")
                        .build()),
                ToolExecutionResultMessage.from("id", "previous", "result"));

        InvocationContext invocationContext = InvocationContext.builder().build();

        ToolProvider dynamicProvider = request -> ToolProviderResult.builder()
                .add(ToolSpecification.builder().name("dynamic_tool").build(), (req, memoryId) -> "ok")
                .build();

        ToolServiceContext context = contextWith(dynamicProvider);
        ToolServiceContext refreshed = refreshDynamicProviders(context, messages, invocationContext);

        assertThat(refreshed).isSameAs(context);
        assertThat(refreshed.toolExecutors()).doesNotContainKey("dynamic_tool");
    }

    @Test
    void should_attach_a_failing_listener_exception_to_the_tool_error_of_a_failed_round() {
        AiServiceListenerRegistrar registrar = AiServiceListenerRegistrar.newInstance(true);
        RuntimeException listenerError = new RuntimeException("listener failed");
        registrar.register((ToolExecutedEventListener) event -> {
            throw listenerError;
        });
        ToolExecutionRequest succeeded =
                ToolExecutionRequest.builder().id("1").name("succeed").arguments("{}").build();
        ToolExecutionRequest failed =
                ToolExecutionRequest.builder().id("2").name("fail").arguments("{}").build();
        RuntimeException toolError = new RuntimeException("tool failed");

        ToolService.fireToolExecutedEventsOfFailedRound(
                InvocationContext.builder().build(),
                List.of(succeeded, failed),
                Map.of(succeeded, ToolExecutionResult.builder().resultText("ok").build()),
                Map.of(failed, toolError),
                registrar);

        assertThat(toolError.getSuppressed()).containsExactly(listenerError);
    }

    private static final Map<String, ToolExecutor> BOOK_ROOM_EXECUTORS =
            Map.of("bookRoom", (request, memoryId) -> "booked");

    // separate but equal tool calls, as from a provider that sends no tool call ids (for example Gemini)
    private static ToolExecutionRequest bookRoomWithoutId() {
        return ToolExecutionRequest.builder().name("bookRoom").arguments("{}").build();
    }

    private static List<String> toolResultTexts(ChatMemory memory) {
        return memory.messages().stream()
                .map(message -> ((ToolExecutionResultMessage) message).text())
                .toList();
    }

    @Test
    void failRoundBeforeRethrow_should_not_store_a_successful_result_for_an_equal_tool_call_that_failed() {
        ToolExecutionRequest first = bookRoomWithoutId();
        ToolExecutionRequest second = bookRoomWithoutId();
        RuntimeException toolError = new RuntimeException("No room left in hotel 42");
        Map<ToolExecutionRequest, ToolExecutionResult> results = new LinkedHashMap<>();
        results.put(first, ToolExecutionResult.builder().resultText("booked").build());
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        new ToolService()
                .failRoundBeforeRethrow(
                        List.of(first, second),
                        results,
                        Map.of(second, toolError),
                        toolError,
                        BOOK_ROOM_EXECUTORS,
                        null,
                        memory,
                        null,
                        DUMMY_CONTEXT,
                        AiServiceListenerRegistrar.newInstance(false));

        assertThat(toolResultTexts(memory))
                .containsExactly("booked", "Tool 'bookRoom' failed, so the request was stopped");
    }

    @Test
    void saveResultsOfFailedRoundAsync_should_not_store_the_tool_error_of_equal_tool_calls() {
        ToolExecutionRequest first = bookRoomWithoutId();
        ToolExecutionRequest second = bookRoomWithoutId();
        RuntimeException toolError = new RuntimeException("No room left in hotel 42");
        // like the started tools of a round: equal calls share one entry, with the future of the last one
        Map<ToolExecutionRequest, CompletableFuture<ToolExecutionResult>> toolFutures = new LinkedHashMap<>();
        toolFutures.put(first, CompletableFuture.completedFuture(ToolExecutionResult.builder().resultText("booked").build()));
        toolFutures.put(second, CompletableFuture.failedFuture(toolError));
        ToolExecutionResultMessage withToolError = ToolExecutionResultMessage.builder()
                .toolName("bookRoom")
                .text(toolError.getMessage())
                .isError(true)
                .build();
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        assertThat(new ToolService()
                        .saveResultsOfFailedRoundAsync(
                                memory,
                                List.of(first, second),
                                List.of(withToolError, withToolError),
                                toolFutures,
                                BOOK_ROOM_EXECUTORS,
                                toolError))
                .succeedsWithin(Duration.ofSeconds(1));

        assertThat(toolResultTexts(memory))
                .containsExactly(
                        "Tool 'bookRoom' failed, so the request was stopped",
                        "Tool 'bookRoom' failed, so the request was stopped");
    }

    // a memory whose asynchronous add fails with the given error
    private static class FailingChatMemory implements ChatMemory {

        private final RuntimeException error;

        FailingChatMemory(RuntimeException error) {
            this.error = error;
        }

        @Override
        public Object id() {
            return "default";
        }

        @Override
        public void add(ChatMessage message) {
            throw error;
        }

        @Override
        public List<ChatMessage> messages() {
            return List.of();
        }

        @Override
        public void clear() {}

        @Override
        public CompletableFuture<Void> addAsync(List<ChatMessage> messages) {
            return CompletableFuture.failedFuture(error);
        }
    }

    @Test
    void saveResultsOfFailedRoundAsync_should_not_suppress_the_tool_error_in_itself() {
        ToolExecutionRequest request = bookRoomWithoutId();
        RuntimeException error = new RuntimeException("the store and the tool share this error");
        CompletableFuture<Void> saved = new ToolService()
                .saveResultsOfFailedRoundAsync(
                        new FailingChatMemory(error),
                        List.of(request),
                        List.of(ToolExecutionResultMessage.from(request, "booked")),
                        Map.of(request, CompletableFuture.failedFuture(error)),
                        BOOK_ROOM_EXECUTORS,
                        error);

        assertThat(saved).succeedsWithin(Duration.ofSeconds(1));
        assertThat(error.getSuppressed()).isEmpty();
    }
}
