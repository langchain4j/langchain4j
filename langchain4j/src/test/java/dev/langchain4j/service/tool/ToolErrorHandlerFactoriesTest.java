package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.exception.ToolErrorVisibleToLlm;
import dev.langchain4j.invocation.InvocationContext;
import java.io.IOException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ToolErrorHandlerFactoriesTest {

    private static final ToolErrorContext CONTEXT = ToolErrorContext.builder()
            .toolExecutionRequest(
                    ToolExecutionRequest.builder().name("test").arguments("{}").build())
            .invocationContext(InvocationContext.builder().build())
            .build();

    @Test
    void fail_invocation_should_propagate_runtime_exception_as_is() {

        RuntimeException error = new IllegalStateException("boom");

        assertThatThrownBy(() -> ToolExecutionErrorHandler.failInvocation().handle(error, CONTEXT))
                .isSameAs(error);
        assertThatThrownBy(() -> ToolArgumentsErrorHandler.failInvocation().handle(error, CONTEXT))
                .isSameAs(error);
    }

    @Test
    void fail_invocation_should_wrap_checked_exception() {

        Exception error = new IOException("boom");

        assertThatThrownBy(() -> ToolExecutionErrorHandler.failInvocation().handle(error, CONTEXT))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasCause(error);
    }

    @Test
    void send_exception_message_to_llm_should_send_the_message_of_the_error() {

        Exception error = new IllegalStateException("Order 42 was not found");

        assertThat(ToolExecutionErrorHandler.sendExceptionMessageToLlm().handle(error, CONTEXT))
                .isEqualTo(ToolErrorHandlerResult.text("Order 42 was not found"));
        assertThat(ToolArgumentsErrorHandler.sendExceptionMessageToLlm().handle(error, CONTEXT))
                .isEqualTo(ToolErrorHandlerResult.text("Order 42 was not found"));
    }

    @Test
    void send_exception_message_to_llm_should_fall_back_to_the_type_of_the_error_when_there_is_no_message() {

        assertThat(ToolExecutionErrorHandler.sendExceptionMessageToLlm().handle(new IllegalStateException(), CONTEXT))
                .isEqualTo(ToolErrorHandlerResult.text(IllegalStateException.class.getName()));
    }

    static class OrderNotFoundException extends RuntimeException implements ToolErrorVisibleToLlm {

        OrderNotFoundException() {
            super("order 42 missing in table ORDERS");
        }

        @Override
        public String messageForLlm() {
            return "There is no order with this ID.";
        }
    }

    static class BlankMessageException extends RuntimeException implements ToolErrorVisibleToLlm {

        @Override
        public String messageForLlm() {
            return "  ";
        }
    }

    @Test
    void fail_unless_visible_to_llm_should_send_the_message_written_for_the_llm() {

        assertThat(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm()
                        .handle(new OrderNotFoundException(), CONTEXT))
                .as("the LLM sees the authored text, not the message of the exception")
                .isEqualTo(ToolErrorHandlerResult.text("There is no order with this ID."));
    }

    @Test
    void fail_unless_visible_to_llm_should_fail_for_any_other_exception() {

        RuntimeException error = new IllegalStateException("jdbc:postgresql://db:5432/prod?password=hunter2");

        assertThatThrownBy(() ->
                        ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm().handle(error, CONTEXT))
                .isSameAs(error);
    }

    @Test
    void fail_unless_visible_to_llm_should_fail_when_the_message_for_the_llm_is_blank() {

        RuntimeException error = new BlankMessageException();

        assertThatThrownBy(() ->
                        ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm().handle(error, CONTEXT))
                .isSameAs(error);
    }

    static class Tools {

        @Tool("Returns the status of an order")
        String orderStatus(String orderId) {
            throw ToolErrorVisibleToLlm.from("There is no order with this ID.", new IllegalStateException("ORA-00942"));
        }
    }

    @Test
    void marker_should_survive_the_wrapping_done_by_the_tool_executor() throws Exception {

        Method method = Tools.class.getDeclaredMethod("orderStatus", String.class);
        ToolExecutor executor = DefaultToolExecutor.builder()
                .object(new Tools())
                .originalMethod(method)
                .methodToInvoke(method)
                .wrapToolArgumentsExceptions(true)
                .propagateToolExecutionExceptions(true)
                .build();

        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                ToolExecutionRequest.builder()
                        .name("orderStatus")
                        .arguments("{\"arg0\": \"42\"}")
                        .build(),
                executor,
                InvocationContext.builder().build(),
                ToolArgumentsErrorHandler.failInvocation(),
                ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm());

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText())
                .as("the exception is wrapped in a ToolExecutionException and unwrapped again before the handler sees it")
                .isEqualTo("There is no order with this ID.");
    }

    @Test
    void marker_should_survive_a_custom_tool_executor_that_throws_it_with_a_cause() {

        // a custom ToolExecutor (MCP, a ToolProvider, or a hand-written one) throws the marked exception
        // directly, so ToolService unwraps it down to the cause before the handler sees it
        ToolExecutor executor = (request, context) -> {
            throw ToolErrorVisibleToLlm.from(
                    "The order service is temporarily unavailable.",
                    new IllegalStateException("jdbc:postgresql://db:5432/prod?password=hunter2"));
        };

        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                ToolExecutionRequest.builder().name("orderStatus").arguments("{}").build(),
                executor,
                InvocationContext.builder().build(),
                ToolArgumentsErrorHandler.failInvocation(),
                ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm());

        assertThat(result.resultText()).isEqualTo("The order service is temporarily unavailable.");
        assertThat(result.resultText())
                .as("the cause must never reach the LLM")
                .doesNotContain("hunter2");
    }

    @Test
    void send_exception_message_to_llm_should_not_send_the_cause_of_a_marked_exception() {

        ToolExecutor executor = (request, context) -> {
            throw ToolErrorVisibleToLlm.from(
                    "The order service is temporarily unavailable.",
                    new IllegalStateException("jdbc:postgresql://db:5432/prod?password=hunter2"));
        };

        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                ToolExecutionRequest.builder().name("orderStatus").arguments("{}").build(),
                executor,
                InvocationContext.builder().build(),
                ToolArgumentsErrorHandler.failInvocation(),
                ToolExecutionErrorHandler.sendExceptionMessageToLlm());

        assertThat(result.resultText()).doesNotContain("hunter2");
    }

    @Test
    void default_handler_should_honor_the_marker_for_a_tool_method() {

        ToolExecutor executor = (request, context) -> {
            throw new OrderNotFoundException();
        };

        ToolExecutionResult result = executeWithDefaultHandlers(executor);

        assertThat(result.resultText())
                .as("without any configuration, an exception that says what the LLM may be told is honored")
                .isEqualTo("There is no order with this ID.");
    }

    @Test
    void default_handler_should_not_send_the_cause_of_a_marked_exception() {

        ToolExecutor executor = (request, context) -> {
            throw ToolErrorVisibleToLlm.from(
                    "The order service is temporarily unavailable.",
                    new IllegalStateException("jdbc:postgresql://db:5432/prod?password=hunter2"));
        };

        ToolExecutionResult result = executeWithDefaultHandlers(executor);

        assertThat(result.resultText()).isEqualTo("The order service is temporarily unavailable.");
        assertThat(result.resultText())
                .as("the cause must never reach the LLM, not even without a configured handler")
                .doesNotContain("hunter2");
    }

    private static ToolExecutionResult executeWithDefaultHandlers(ToolExecutor executor) {
        ToolService toolService = new ToolService();
        return ToolService.executeWithErrorHandling(
                ToolExecutionRequest.builder().name("orderStatus").arguments("{}").build(),
                executor,
                InvocationContext.builder().build(),
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler());
    }

    @Test
    void asynchronous_default_should_honor_the_marker() {

        ToolService toolService = new ToolService();

        assertThat(toolService.asyncExecutionErrorHandler().handle(new OrderNotFoundException(), CONTEXT))
                .as("an MCP server error, or any marked exception, reaches the LLM in the asynchronous modes too")
                .isEqualTo(ToolErrorHandlerResult.text("There is no order with this ID."));

        RuntimeException unmarked = new IllegalStateException("boom");
        assertThatThrownBy(() -> toolService.asyncExecutionErrorHandler().handle(unmarked, CONTEXT))
                .isSameAs(unmarked);
    }
}
