package dev.langchain4j.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool provider backed by one or more MCP clients.
 */
public class McpToolProvider implements ToolProvider {

    private final List<McpClient> mcpClients;
    private final boolean failIfOneServerFails;
    private final BiPredicate<McpClient, ToolSpecification> mcpToolsFilter;
    private static final Logger log = LoggerFactory.getLogger(McpToolProvider.class);

    private McpToolProvider(Builder builder) {
        this.mcpClients = new ArrayList<>(builder.mcpClients);
        this.failIfOneServerFails = Utils.getOrDefault(builder.failIfOneServerFails, false);
        this.mcpToolsFilter = builder.mcpToolsFilter;
    }

    protected McpToolProvider(List<McpClient> mcpClients, boolean failIfOneServerFails, BiPredicate<McpClient, ToolSpecification> mcpToolsFilter) {
        this.mcpClients = mcpClients;
        this.failIfOneServerFails = failIfOneServerFails;
        this.mcpToolsFilter = mcpToolsFilter;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        return provideTools(request, mcpToolsFilter);
    }

    protected ToolProviderResult provideTools(ToolProviderRequest request, BiPredicate<McpClient, ToolSpecification> mcpToolsFilter) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (McpClient mcpClient : mcpClients) {
            try {
                mcpClient.listTools().stream().filter(tool -> mcpToolsFilter.test(mcpClient, tool))
                        .forEach(toolSpecification -> {
                    builder.add(toolSpecification, (executionRequest, memoryId) -> mcpClient.executeTool(executionRequest));
                });
            } catch (IllegalConfigurationException e) {
                throw e;
            } catch (Exception e) {
                if (failIfOneServerFails) {
                    throw new RuntimeException("Failed to retrieve tools from MCP server", e);
                } else {
                    log.warn("Failed to retrieve tools from MCP server", e);
                }
            }
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<McpClient> mcpClients;
        private Boolean failIfOneServerFails;
        private BiPredicate<McpClient, ToolSpecification> mcpToolsFilter = (mcp, tool) -> true;

        /**
         * The list of MCP clients to use for retrieving tools.
         */
        public McpToolProvider.Builder mcpClients(List<McpClient> mcpClients) {
            this.mcpClients = mcpClients;
            return this;
        }

        /**
         * The list of MCP clients to use for retrieving tools.
         */
        public McpToolProvider.Builder mcpClients(McpClient... mcpClients) {
            return mcpClients(Arrays.asList(mcpClients));
        }

        /**
         * The predicate to filter MCP provided tools.
         */
        public McpToolProvider.Builder filter(BiPredicate<McpClient, ToolSpecification> mcpToolsFilter) {
            this.mcpToolsFilter = this.mcpToolsFilter.and(mcpToolsFilter);
            return this;
        }

        /**
         * Filter MCP provided tools with a specific name.
         */
        public McpToolProvider.Builder filterToolNames(String... toolNames) {
            return filter(new ToolsNameFilter(toolNames));
        }

        /**
         * If this is true, then the tool provider will throw an exception if it fails to list tools from any of the servers.
         * If this is false (default), then the tool provider will ignore the error and continue with the next server.
         */
        public McpToolProvider.Builder failIfOneServerFails(boolean failIfOneServerFails) {
            this.failIfOneServerFails = failIfOneServerFails;
            return this;
        }

        public McpToolProvider build() {
            return new McpToolProvider(this);
        }
    }

    private static class ToolsNameFilter implements BiPredicate<McpClient, ToolSpecification> {
        private final List<String> toolNames;

        private ToolsNameFilter(String... toolNames) {
            this(Arrays.asList(toolNames));
        }

        private ToolsNameFilter(List<String> toolNames) {
            this.toolNames = toolNames;
        }

        @Override
        public boolean test(McpClient mcpClient, ToolSpecification tool) {
            return toolNames.stream().anyMatch(name -> name.equals(tool.name()));
        }
    }
}
