package dev.langchain4j.service.tool;

import static dev.langchain4j.service.tool.ToolService.executeWithErrorHandling;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Verifies that tool execution errors are logged only in the default configuration (when the exception
 * is also sent to the LLM), and not when the user customizes the error-handling behavior.
 * <p>
 * These tests capture {@link System#err} (the default tinylog destination for WARN), so the whole class
 * runs {@link Isolated} and single-threaded to avoid races with the otherwise parallel test suite.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class ToolErrorLoggingTest {

    private static final ToolExecutionRequest DUMMY_REQUEST =
            ToolExecutionRequest.builder().name("test").arguments("{}").build();

    private static final InvocationContext DUMMY_CONTEXT =
            InvocationContext.builder().build();

    private static final ToolArgumentsErrorHandler DEFAULT_ARGS_HANDLER = (error, ctx) -> {
        if (error instanceof RuntimeException re) throw re;
        throw new RuntimeException(error);
    };

    @Test
    void default_execution_error_handler_logs_exception_at_warn() {
        ToolExecutor executor = (req, ctx) -> {
            throw new IllegalStateException("default-handler-failure");
        };
        ToolExecutionErrorHandler defaultHandler = new ToolService().executionErrorHandler();

        String logOutput = captureStdErr(() ->
                executeWithErrorHandling(DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, defaultHandler));

        assertThat(logOutput)
                .as(
                        "default behavior must log the exception (with stack trace) so the user sees what was sent to the LLM")
                .contains("WARN")
                .contains("test")
                .contains("default-handler-failure")
                .contains(IllegalStateException.class.getName());
    }

    @Test
    void custom_execution_error_handler_does_not_log() {
        ToolExecutor executor = (req, ctx) -> {
            throw new IllegalStateException("custom-handler-failure");
        };
        ToolExecutionErrorHandler customHandler = (error, ctx) -> ToolErrorHandlerResult.text("handled by custom");

        String logOutput = captureStdErr(() ->
                executeWithErrorHandling(DUMMY_REQUEST, executor, DUMMY_CONTEXT, DEFAULT_ARGS_HANDLER, customHandler));

        assertThat(logOutput)
                .as("when the user customizes the error handler, the framework must not log the exception")
                .doesNotContain("custom-handler-failure")
                .doesNotContain(IllegalStateException.class.getName());
    }

    private static String captureStdErr(Supplier<?> action) {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.get();
        } finally {
            System.setErr(originalErr);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}
