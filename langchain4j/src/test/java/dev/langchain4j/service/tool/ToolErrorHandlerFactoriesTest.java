package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
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
    void fail_ai_service_invocation_should_propagate_runtime_exception_as_is() {

        RuntimeException error = new IllegalStateException("boom");

        assertThatThrownBy(() -> ToolExecutionErrorHandler.failAiServiceInvocation().handle(error, CONTEXT))
                .isSameAs(error);
        assertThatThrownBy(() -> ToolArgumentsErrorHandler.failAiServiceInvocation().handle(error, CONTEXT))
                .isSameAs(error);
    }

    @Test
    void fail_ai_service_invocation_should_wrap_checked_exception() {

        Exception error = new IOException("boom");

        assertThatThrownBy(() -> ToolExecutionErrorHandler.failAiServiceInvocation().handle(error, CONTEXT))
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

    static class LibraryException extends RuntimeException {

        LibraryException(String message) {
            super(message);
        }
    }

    static class LibrarySubException extends LibraryException {

        LibrarySubException(String message) {
            super(message);
        }
    }

    @Test
    void fail_unless_visible_to_llm_should_send_the_message_written_for_the_llm() {

        assertThat(ToolExecutionErrorHandler.failUnlessVisibleToLlm()
                        .handle(new OrderNotFoundException(), CONTEXT))
                .as("the LLM sees the authored text, not the message of the exception")
                .isEqualTo(ToolErrorHandlerResult.text("There is no order with this ID."));
    }

    @Test
    void fail_unless_visible_to_llm_should_fail_for_any_other_exception() {

        RuntimeException error = new IllegalStateException("jdbc:postgresql://db:5432/prod?password=hunter2");

        assertThatThrownBy(() ->
                        ToolExecutionErrorHandler.failUnlessVisibleToLlm().handle(error, CONTEXT))
                .isSameAs(error);
    }

    @Test
    void fail_unless_visible_to_llm_should_fail_when_the_message_for_the_llm_is_blank() {

        RuntimeException error = new BlankMessageException();

        assertThatThrownBy(() ->
                        ToolExecutionErrorHandler.failUnlessVisibleToLlm().handle(error, CONTEXT))
                .isSameAs(error);
    }

    @Test
    void send_exception_message_to_llm_for_should_send_the_message_of_the_listed_types() {

        ToolExecutionErrorHandler handler =
                ToolExecutionErrorHandler.sendExceptionMessageToLlmFor(LibraryException.class);

        assertThat(handler.handle(new LibraryException("not found"), CONTEXT))
                .isEqualTo(ToolErrorHandlerResult.text("not found"));
        assertThat(handler.handle(new LibrarySubException("also not found"), CONTEXT))
                .as("subtypes of a listed type are visible as well")
                .isEqualTo(ToolErrorHandlerResult.text("also not found"));
    }

    @Test
    void send_exception_message_to_llm_for_should_fail_for_types_that_are_not_listed() {

        RuntimeException error = new IllegalStateException("boom");

        assertThatThrownBy(() -> ToolExecutionErrorHandler.sendExceptionMessageToLlmFor(LibraryException.class)
                        .handle(error, CONTEXT))
                .isSameAs(error);
    }

    @Test
    void send_exception_message_to_llm_for_should_still_honor_the_marker() {

        assertThat(ToolExecutionErrorHandler.sendExceptionMessageToLlmFor(LibraryException.class)
                        .handle(new OrderNotFoundException(), CONTEXT))
                .as("a marked exception is sent even when it is not listed, using the text written for the LLM")
                .isEqualTo(ToolErrorHandlerResult.text("There is no order with this ID."));
    }

    @Test
    void send_exception_message_to_llm_for_should_reject_an_empty_list_of_types() {

        assertThatThrownBy(ToolExecutionErrorHandler::sendExceptionMessageToLlmFor)
                .isInstanceOf(IllegalArgumentException.class);
    }

    static class Tools {

        @Tool("Returns the status of an order")
        String orderStatus(String orderId) {
            throw ToolErrorVisibleToLlm.of("There is no order with this ID.", new IllegalStateException("ORA-00942"));
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
                ToolArgumentsErrorHandler.failAiServiceInvocation(),
                ToolExecutionErrorHandler.failUnlessVisibleToLlm());

        assertThat(result.isError()).isTrue();
        assertThat(result.resultText())
                .as("the exception is wrapped in a ToolExecutionException and unwrapped again before the handler sees it")
                .isEqualTo("There is no order with this ID.");
    }
}
