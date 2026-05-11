package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

/**
 * Validates {@link AiServices#errorHandlerBypass(java.util.function.Predicate)}: when the
 * configured predicate returns {@code true} for an exception thrown by a tool, the exception
 * propagates unchanged to the caller instead of being routed through the
 * {@link ToolExecutionErrorHandler}.
 *
 * <p>This is the hook downstream framework integrators (e.g. Quarkus) use to surface
 * marker-typed exceptions (authorization or guardrail violations) verbatim.
 */
class ErrorHandlerBypassTest {

    interface Assistant {
        String chat(String message);
    }

    /** A marker-typed RuntimeException used by the test. */
    static class MarkerException extends RuntimeException {
        MarkerException(String msg) {
            super(msg);
        }
    }

    static class ExplodingTool {
        @Tool
        public String boom(String arg) {
            throw new MarkerException("kaboom:" + arg);
        }
    }

    @Test
    void without_bypass_predicate_exceptions_are_routed_through_error_handler() {
        ExplodingTool tool = new ExplodingTool();
        // 1st response: tool call. 2nd response (after error handler turns the exception into
        // text): a final text answer "ok".
        AiMessage toolCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("c1")
                .name("boom")
                .arguments("{\"arg0\": \"x\"}")
                .build());
        AiMessage finalAnswer = AiMessage.from("ok");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(toolCall, finalAnswer);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .build();

        // Default behaviour: the exception is captured by the default ToolExecutionErrorHandler
        // and turned into a string sent back to the LLM. The LLM then produces "ok".
        assertThatNoException().isThrownBy(() -> {
            String result = assistant.chat("go");
            assertThat(result).isEqualTo("ok");
        });
    }

    @Test
    void with_bypass_predicate_matching_exceptions_propagate_unchanged() {
        ExplodingTool tool = new ExplodingTool();
        AiMessage toolCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("c1")
                .name("boom")
                .arguments("{\"arg0\": \"x\"}")
                .build());
        AiMessage finalAnswer = AiMessage.from("ok");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(toolCall, finalAnswer);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                // DefaultToolExecutor wraps tool-thrown RuntimeExceptions in ToolExecutionException,
                // so the predicate inspects the cause. This mirrors how Quarkus's predicate is
                // typically structured against its PreventsErrorHandlerExecution marker.
                .errorHandlerBypass(e -> e instanceof ToolExecutionException && e.getCause() instanceof MarkerException)
                .build();

        // DefaultToolExecutor wraps the tool's RuntimeException in ToolExecutionException; the
        // bypass hook rethrows that wrapper unchanged. The MarkerException is preserved as the
        // cause, exactly as it would be without the hook — but importantly the error handler did
        // not run and turn it into a string sent to the LLM.
        assertThatExceptionOfType(ToolExecutionException.class)
                .isThrownBy(() -> assistant.chat("go"))
                .withCauseInstanceOf(MarkerException.class)
                .havingCause()
                .withMessageContaining("kaboom:x");
    }

    @Test
    void with_bypass_predicate_non_matching_exceptions_still_route_through_error_handler() {
        // Predicate returns false: error handler intercepts and the loop proceeds normally.
        ExplodingTool tool = new ExplodingTool();
        AiMessage toolCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("c1")
                .name("boom")
                .arguments("{\"arg0\": \"x\"}")
                .build());
        AiMessage finalAnswer = AiMessage.from("ok");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(toolCall, finalAnswer);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(tool)
                .errorHandlerBypass(e -> false)
                .build();

        String result = assistant.chat("go");
        assertThat(result).isEqualTo("ok");
    }
}
