package dev.langchain4j.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool provider backed by one or more MCP clients.
 */
public class McpToolProvider implements ToolProvider {

    private final CopyOnWriteArrayList<McpClient> mcpClients;
    private final boolean failIfOneServerFails;
    private final AtomicReference<BiPredicate<McpClient, ToolSpecification>> mcpToolsFilter;
    private final Function<ToolExecutor, ToolExecutor> toolWrapper;
    private static final Logger log = LoggerFactory.getLogger(McpToolProvider.class);

    private McpToolProvider(Builder builder) {
        this(builder.mcpClients, Utils.getOrDefault(builder.failIfOneServerFails, false), builder.mcpToolsFilter, builder.toolWrapper);
    }

    protected McpToolProvider(List<McpClient> mcpClients, boolean failIfOneServerFails, BiPredicate<McpClient, ToolSpecification> mcpToolsFilter) {
        this(Objects.requireNonNull(mcpClients), failIfOneServerFails, mcpToolsFilter, Function.identity());
    }

    protected McpToolProvider(List<McpClient> mcpClients, boolean failIfOneServerFails, BiPredicate<McpClient, ToolSpecification> mcpToolsFilter, Function<ToolExecutor, ToolExecutor> toolWrapper) {
        this.mcpClients = new CopyOnWriteArrayList<>(mcpClients);
        this.failIfOneServerFails = failIfOneServerFails;
        this.mcpToolsFilter = new AtomicReference<>(mcpToolsFilter);
        this.toolWrapper = toolWrapper;
    }

    /**
     * Adds a new MCP client to the list of clients.
     *
     * @param client the MCP client to add
     */
    public void addMcpClient(McpClient client) {
        Objects.requireNonNull(client);
        mcpClients.add(client);
    }

    /**
     * Removes an MCP client from the list of clients.
     *
     * @param client the MCP client to remove
     */
    public void removeMcpClient(McpClient client) {
        mcpClients.remove(client);
    }

    /**
     * Adds a tools filter that will act in conjunction (AND) with the eventually existing ones.
     *
     * @param filter the filter to add
     */
    public void addFilter(BiPredicate<McpClient, ToolSpecification> filter) {
        Objects.requireNonNull(filter);
        BiPredicate<McpClient, ToolSpecification> currentFilter = mcpToolsFilter.get();
        while (!mcpToolsFilter.compareAndSet(currentFilter, currentFilter.and(filter))) {
            currentFilter = mcpToolsFilter.get();
        }
    }

    /**
     * Sets the tools filter overriding the eventually existing ones.
     *
     * @param filter the filter to add
     */
    public void setFilter(BiPredicate<McpClient, ToolSpecification> filter) {
        Objects.requireNonNull(filter);
        BiPredicate<McpClient, ToolSpecification> currentFilter = mcpToolsFilter.get();
        while (!mcpToolsFilter.compareAndSet(currentFilter, filter)) {
            currentFilter = mcpToolsFilter.get();
        }
    }

    /**
     * Resets the all the eventually existing tools filters.
     */
    public void resetFilters() {
        setFilter((mcp, tool) -> true);
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        return provideTools(request, mcpToolsFilter.get());
    }

    protected ToolProviderResult provideTools(ToolProviderRequest request, BiPredicate<McpClient, ToolSpecification> mcpToolsFilter) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (McpClient mcpClient : mcpClients) {
            var defaultToolExecutor = new DefaultToolExecutor(mcpClient);
            try {
                mcpClient.listTools().stream().filter(tool -> mcpToolsFilter.test(mcpClient, tool))
                        .forEach(toolSpecification -> {
                    builder.add(toolSpecification, toolWrapper.apply(defaultToolExecutor));
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
        private Function<ToolExecutor, ToolExecutor> toolWrapper = Function.identity();

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

        /**
         * Provide a wrapper around the {@link ToolExecutor} that can be used to implement tracing for example.
         */
        public McpToolProvider.Builder toolWrapper(Function<ToolExecutor, ToolExecutor> toolWrapper) {
            this.toolWrapper = toolWrapper;
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

    private static class DefaultToolExecutor implements ToolExecutor {
        private final McpClient mcpClient;

        public DefaultToolExecutor(McpClient mcpClient) {
            this.mcpClient = mcpClient;
        }

        @Override
        public String execute(ToolExecutionRequest executionRequest, Object memoryId) {
            return mcpClient.executeTool(executionRequest);
        }
    }
}
