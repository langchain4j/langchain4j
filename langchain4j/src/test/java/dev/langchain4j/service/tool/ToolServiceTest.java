package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.invocation.InvocationContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dev.langchain4j.service.tool.ToolService.executeWithErrorHandling;
import static dev.langchain4j.service.tool.ToolService.shouldReturnImmediately;
import static org.assertj.core.api.Assertions.*;

class ToolServiceTest {

    private static final ToolExecutionRequest DUMMY_REQUEST = ToolExecutionRequest.builder()
            .name("test")
            .arguments("{}")
            .build();

    private static final InvocationContext DUMMY_CONTEXT = InvocationContext.builder().build();

    private static final ToolArgumentsErrorHandler DEFAULT_ARGS_HANDLER = (error, ctx) -> {
        if (error instanceof RuntimeException re) throw re;
        throw new RuntimeException(error);
    };

    private static final ToolExecutionErrorHandler DEFAULT_EXEC_HANDLER = (error, ctx) ->
            ToolErrorHandlerResult.text(error.getMessage());

    @Test
    void shouldReturnImmediately_empty_list_should_not_crash() {
        assertThatNoException().isThrownBy(() -> shouldReturnImmediately(false, List.of()));
    }

    // --- propagateException tests ---

    @Test
    void propagateException_rethrows_original_RuntimeException() {
        RuntimeException original = new IllegalStateException("boom");
        ToolExecutor executor = (req, ctx) -> { throw original; };

        ToolExecutionErrorHandler handler = (error, ctx) -> ToolErrorHandlerResult.propagateException();

        assertThatThrownBy(() -> executeWithErrorHandling(
                DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, handler))
                .isSameAs(original);
    }

    @Test
    void propagateException_wraps_checked_exception() {
        IOException checked = new IOException("disk error");
        ToolExecutor executor = (req, ctx) -> { throw new RuntimeException(checked); };

        ToolExecutionErrorHandler handler = (error, ctx) -> ToolErrorHandlerResult.propagateException();

        assertThatThrownBy(() -> executeWithErrorHandling(
                DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, handler))
                .isInstanceOf(RuntimeException.class)
                .hasCause(checked);
    }

    @Test
    void propagateException_false_by_default() {
        assertThat(ToolErrorHandlerResult.text("err").shouldPropagateException()).isFalse();
    }

    @Test
    void text_factory_does_not_propagate() {
        RuntimeException original = new RuntimeException("fail");
        ToolExecutor executor = (req, ctx) -> { throw original; };

        ToolExecutionResult result = executeWithErrorHandling(
                DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, DEFAULT_EXEC_HANDLER);

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).isEqualTo("fail");
    }

    // --- rawError tests ---

    @Test
    void rawError_available_in_context() {
        RuntimeException original = new RuntimeException("outer", new IllegalArgumentException("inner"));
        ToolExecutor executor = (req, ctx) -> { throw original; };

        ToolExecutionErrorHandler handler = (error, ctx) -> {
            assertThat(ctx.rawError()).isSameAs(original);
            assertThat(error).isSameAs(original.getCause());
            return ToolErrorHandlerResult.text("handled");
        };

        ToolExecutionResult result = executeWithErrorHandling(
                DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, handler);

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
        ToolExecutor executor = (req, ctx) -> { throw wrapper; };

        ToolExecutionErrorHandler handler = (error, ctx) -> {
            assertThat(error).isSameAs(cause);
            assertThat(ctx.rawError()).isSameAs(wrapper);
            return ToolErrorHandlerResult.text("ok");
        };

        executeWithErrorHandling(DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, handler);
    }

    // --- propagateException with ToolArgumentsException ---

    @Test
    void propagateException_works_with_ToolArgumentsException() {
        ToolArgumentsException original = new ToolArgumentsException("bad args");
        ToolExecutor executor = (req, ctx) -> { throw original; };

        ToolArgumentsErrorHandler argsHandler = (error, ctx) -> ToolErrorHandlerResult.propagateException();

        assertThatThrownBy(() -> executeWithErrorHandling(
                DUMMY_REQUEST, executor, DUMMY_CONTEXT, argsHandler, DEFAULT_EXEC_HANDLER))
                .isSameAs(original);
    }

    // --- propagateException through internalExecuteTool ---

    @Test
    void propagateException_in_tool_loop() {
        RuntimeException original = new IllegalStateException("must propagate");
        ToolExecutor executor = (req, ctx) -> { throw original; };

        ToolService toolService = new ToolService();
        toolService.executionErrorHandler((error, ctx) -> ToolErrorHandlerResult.propagateException());

        Map<String, ToolExecutor> executors = Map.of("test", executor);

        assertThatThrownBy(() -> toolService.executeTool(
                DUMMY_CONTEXT, executors, DUMMY_REQUEST, null, null))
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
}
