package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolErrorVisibleToLlm;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.junit.jupiter.api.Test;

/**
 * An application-level error ("Tool Execution Error" in the MCP specification) is the way an MCP server
 * tells the model that a tool did not succeed, so its text must be able to reach the LLM. A protocol
 * error, on the other hand, means the call itself went wrong and must not be hidden from the application.
 */
class McpApplicationErrorTest {

    private static final McpToolResultConverter CONVERTER = new DefaultMcpToolResultConverter();

    @Test
    void application_level_error_in_content_should_be_visible_to_the_llm() {

        String response =
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "isError": true,
                    "content": [{"type": "text", "text": "There is no order with this ID."}]
                  }
                }
                """;

        assertThatThrownBy(() -> ToolExecutionHelper.extractResult(response, false, CONVERTER))
                .isInstanceOf(ToolExecutionException.class)
                .isInstanceOf(ToolErrorVisibleToLlm.class)
                .hasMessage("There is no order with this ID.");
    }

    @Test
    void application_level_error_in_structured_content_should_be_visible_to_the_llm() {

        String response =
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "isError": true,
                    "structuredContent": {"reason": "unknown order"}
                  }
                }
                """;

        assertThatThrownBy(() -> ToolExecutionHelper.extractResult(response, false, CONVERTER))
                .isInstanceOf(ToolErrorVisibleToLlm.class)
                .hasMessageContaining("unknown order");
    }

    @Test
    void protocol_error_should_not_be_visible_to_the_llm() {

        String response =
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "error": {"code": -32000, "message": "Internal server error at /opt/mcp/orders.py:88"}
                }
                """;

        assertThatThrownBy(() -> ToolExecutionHelper.extractResult(response, false, CONVERTER))
                .isInstanceOf(ToolExecutionException.class)
                .isNotInstanceOf(ToolErrorVisibleToLlm.class);
    }

    @Test
    void the_error_handler_that_honors_the_marker_should_send_the_text_of_the_server_to_the_llm() {

        String response =
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "isError": true,
                    "content": [{"type": "text", "text": "There is no order with this ID."}]
                  }
                }
                """;

        Throwable applicationError = catchError(response);
        Throwable protocolError = catchError(
                """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "error": {"code": -32000, "message": "Internal server error at /opt/mcp/orders.py:88"}
                }
                """);

        ToolExecutionErrorHandler handler = ToolExecutionErrorHandler.failUnlessVisibleToLlm();

        assertThat(handler.handle(applicationError, errorContext()))
                .isEqualTo(ToolErrorHandlerResult.text("There is no order with this ID."));
        assertThatThrownBy(() -> handler.handle(protocolError, errorContext()))
                .as("a protocol error must fail the AI Service invocation instead of reaching the LLM")
                .isSameAs(protocolError);
    }

    private static Throwable catchError(String response) {
        try {
            ToolExecutionHelper.extractResult(response, false, CONVERTER);
            throw new AssertionError("expected the response to be rejected");
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static ToolErrorContext errorContext() {
        return ToolErrorContext.builder()
                .toolExecutionRequest(ToolExecutionRequest.builder()
                        .name("orderStatus")
                        .arguments("{}")
                        .build())
                .invocationContext(InvocationContext.builder().build())
                .build();
    }
}
