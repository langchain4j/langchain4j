package dev.langchain4j.mcp;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolArgumentParsingException;
import dev.langchain4j.service.tool.ToolExecutionException;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * @since 1.4.0
 */
public class McpToolExecutor implements ToolExecutor {

    private final McpClient mcpClient;
    private final boolean propagateToolExecutionException;

    public McpToolExecutor(Builder builder) {
        this.mcpClient = ensureNotNull(builder.mcpClient, "mcpClient");
        this.propagateToolExecutionException = getOrDefault(builder.propagateToolExecutionException, false);
    }

    // TODO right now
    // when can't parse arguments: throws RuntimeException(JsonProcessingException)
    // when can't execute tool: returns String with error

    // TODO make consistent with DefaultToolExecutor

    @Override
    public String execute(ToolExecutionRequest executionRequest, Object memoryId) {
        try {
            return mcpClient.executeTool(executionRequest);
        } catch (ToolArgumentParsingException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e);
            }
        } catch (ToolExecutionException e) {
            if (propagateToolExecutionException) {
                throw e;
            } else {
                return "There was an error executing the tool. The tool returned: " + e.getMessage();
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private McpClient mcpClient;
        private Boolean propagateToolExecutionException;

        public Builder mcpClient(McpClient mcpClient) {
            this.mcpClient = mcpClient;
            return this;
        }

        public Builder propagateToolExecutionException(Boolean propagateToolExecutionException) {
            this.propagateToolExecutionException = propagateToolExecutionException;
            return this;
        }

        public McpToolExecutor build() {
            return new McpToolExecutor(this);
        }
    }
}
