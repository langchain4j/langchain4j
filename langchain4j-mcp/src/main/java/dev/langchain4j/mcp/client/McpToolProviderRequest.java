package dev.langchain4j.mcp.client;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolProviderRequest;
import java.util.function.BiPredicate;

/**
 * An extension of {@link ToolProviderRequest} that includes the ability to
 * filter tools using a {@link BiPredicate} based on the {@link McpClient} and
 * {@link ToolSpecification}.
 *
 * Note that if you use this class and the toolFilter is set, it will override any
 * default filter specified in the {@link dev.langchain4j.mcp.McpToolProvider}.
 */
public class McpToolProviderRequest extends ToolProviderRequest {

    private BiPredicate<McpClient, ToolSpecification> toolFilter;

    public McpToolProviderRequest(Builder builder) {
        super(builder);
        this.toolFilter = builder.toolFilter;
    }

    public BiPredicate<McpClient, ToolSpecification> getToolFilter() {
        return toolFilter;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ToolProviderRequest.Builder {

        private BiPredicate<McpClient, ToolSpecification> toolFilter;

        public Builder toolFilter(BiPredicate<McpClient, ToolSpecification> toolFilter) {
            this.toolFilter = toolFilter;
            return this;
        }

        @Override
        public Builder invocationContext(InvocationContext invocationContext) {
            super.invocationContext(invocationContext);
            return this;
        }

        @Override
        public Builder userMessage(UserMessage userMessage) {
            super.userMessage(userMessage);
            return this;
        }

        public McpToolProviderRequest build() {
            return new McpToolProviderRequest(this);
        }
    }

}
