package dev.langchain4j.mcp.client;

import dev.langchain4j.exception.ToolErrorVisibleToLlm;
import dev.langchain4j.exception.ToolExecutionException;

/**
 * An error that an MCP server reported inside an otherwise successful tool result, by setting
 * {@code isError} to {@code true}. The MCP specification calls this a "Tool Execution Error"
 * and defines it as the way a server tells the <i>model</i> that a tool did not succeed,
 * as opposed to a protocol error, which indicates that the call itself went wrong.
 * <p>
 * Because the text of such an error is written by the server for the model, this exception
 * implements {@link ToolErrorVisibleToLlm}: error handlers that honor that interface send the
 * text to the LLM instead of failing the AI Service invocation.
 * <p>
 * It remains a {@link ToolExecutionException}, so existing code that catches that type is unaffected,
 * while catching this type distinguishes an application-level error from a protocol error.
 *
 * @since 1.21.0
 */
public class McpApplicationErrorException extends ToolExecutionException implements ToolErrorVisibleToLlm {

    public McpApplicationErrorException(String message) {
        // (Throwable) null on purpose: ToolExecutionException(String) synthesises a RuntimeException cause,
        // which would show up as the cause of an error that has none
        super(message, (Throwable) null);
    }

    @Override
    public String messageForLlm() {
        return getMessage();
    }
}
