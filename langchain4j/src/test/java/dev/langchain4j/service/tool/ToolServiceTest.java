package dev.langchain4j.service.tool;

import static dev.langchain4j.service.tool.ToolService.executeWithErrorHandling;
import static dev.langchain4j.service.tool.ToolService.refreshDynamicProviders;
import static dev.langchain4j.service.tool.ToolService.shouldReturnImmediately;
import static org.assertj.core.api.Assertions.*;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    void shouldReturnImmediately_empty_list_should_not_crash() {
        assertThatNoException().isThrownBy(() -> shouldReturnImmediately(false, List.of()));
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
}
