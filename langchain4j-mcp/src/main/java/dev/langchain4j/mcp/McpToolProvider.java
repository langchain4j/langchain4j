package dev.langchain4j.mcp;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.resourcesastools.McpResourcesAsToolsPresenter;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
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
    private final McpResourcesAsToolsPresenter resourcesAsToolsPresenter;
    private final AtomicReference<BiFunction<McpClient, ToolSpecification, String>> toolNameMapper;
    private final AtomicReference<BiFunction<McpClient, ToolSpecification, ToolSpecification>> toolSpecificationMapper;

    private McpToolProvider(Builder builder) {
        this(
                builder.mcpClients,
                Utils.getOrDefault(builder.failIfOneServerFails, false),
                builder.mcpToolsFilter,
                builder.toolWrapper,
                builder.resourcesAsToolsPresenter,
                builder.toolNameMapper,
                builder.toolSpecificationMapper);
    }

    protected McpToolProvider(
            List<McpClient> mcpClients,
            boolean failIfOneServerFails,
            BiPredicate<McpClient, ToolSpecification> mcpToolsFilter,
            Function<ToolExecutor, ToolExecutor> toolWrapper,
            McpResourcesAsToolsPresenter resourcesAsToolsPresenter,
            BiFunction<McpClient, ToolSpecification, String> toolNameMapper,
            BiFunction<McpClient, ToolSpecification, ToolSpecification> toolSpecificationMapper) {
        this.mcpClients = new CopyOnWriteArrayList<>(mcpClients);
        this.failIfOneServerFails = failIfOneServerFails;
        this.mcpToolsFilter = new AtomicReference<>(mcpToolsFilter);
        this.toolWrapper = toolWrapper;
        this.resourcesAsToolsPresenter = resourcesAsToolsPresenter;
        this.toolNameMapper = new AtomicReference<>(toolNameMapper);
        this.toolSpecificationMapper = new AtomicReference<>(toolSpecificationMapper);
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
     * Sets the tool name mapper overriding the current one.
     * The mapper can be null, in which case the tool names will be used as-is.
     */
    public void setToolNameMapper(BiFunction<McpClient, ToolSpecification, String> toolNameMapper) {
        this.toolNameMapper.set(toolNameMapper);
    }

    /**
     * Sets the tool specification mapper overriding the current one.
     * The mapper can be null, in which case the tool specifications will be used as-is.
     */
    public void setToolSpecificationMapper(
            BiFunction<McpClient, ToolSpecification, ToolSpecification> toolSpecificationMapper) {
        this.toolSpecificationMapper.set(toolSpecificationMapper);
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

    protected ToolProviderResult provideTools(
            ToolProviderRequest request, BiPredicate<McpClient, ToolSpecification> mcpToolsFilter) {
        ToolProviderResult.Builder builder = ToolProviderResult.builder();
        for (McpClient mcpClient : mcpClients) {
            try {
                for (ToolSpecification originalSpec : mcpClient.listTools()) {
                    if (mcpToolsFilter.test(mcpClient, originalSpec)) {
                        BiFunction<McpClient, ToolSpecification, String> nameMapper = toolNameMapper.get();
                        BiFunction<McpClient, ToolSpecification, ToolSpecification> specificationMapper =
                                toolSpecificationMapper.get();
                        ToolSpecification newSpec;
                        // if a tool name mapper or specification mapper is defined, apply it to get the new tool
                        // specification
                        if (nameMapper != null) {
                            newSpec = originalSpec.toBuilder()
                                    .name(nameMapper.apply(mcpClient, originalSpec))
                                    .build();
                        } else if (specificationMapper != null) {
                            newSpec = specificationMapper.apply(mcpClient, originalSpec);
                        } else {
                            newSpec = originalSpec;
                        }
                        // lock down the created McpToolExecutor to the original 'real' tool name, not the mapped one
                        ToolExecutor defaultToolExecutor = new McpToolExecutor(mcpClient, originalSpec.name());
                        builder.add(newSpec, toolWrapper.apply(defaultToolExecutor));
                    }
                }
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
        if (resourcesAsToolsPresenter != null) {
            List<McpClient> mcpClientsUnmodifiable = Collections.unmodifiableList(mcpClients);
            ToolSpecification listResourcesToolSpec = resourcesAsToolsPresenter.createListResourcesSpecification();
            builder.add(
                    listResourcesToolSpec,
                    toolWrapper.apply(resourcesAsToolsPresenter.createListResourcesExecutor(mcpClientsUnmodifiable)));
            ToolSpecification getResourceToolSpec = resourcesAsToolsPresenter.createGetResourceSpecification();
            builder.add(
                    getResourceToolSpec,
                    toolWrapper.apply(resourcesAsToolsPresenter.createGetResourceExecutor(mcpClientsUnmodifiable)));
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private McpResourcesAsToolsPresenter resourcesAsToolsPresenter;
        private List<McpClient> mcpClients;
        private Boolean failIfOneServerFails;
        private BiPredicate<McpClient, ToolSpecification> mcpToolsFilter = (mcp, tool) -> true;
        private Function<ToolExecutor, ToolExecutor> toolWrapper = Function.identity();
        private BiFunction<McpClient, ToolSpecification, String> toolNameMapper;
        private BiFunction<McpClient, ToolSpecification, ToolSpecification> toolSpecificationMapper;

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
         * Filtering is applied *before* the name or specification mapping (see {@link #toolNameMapper(BiFunction)}
         * and {@link #toolSpecificationMapper(BiFunction)}) so
         * it should expect raw tool names as received from the MCP server.
         */
        public McpToolProvider.Builder filter(BiPredicate<McpClient, ToolSpecification> mcpToolsFilter) {
            this.mcpToolsFilter = this.mcpToolsFilter.and(mcpToolsFilter);
            return this;
        }

        /**
         * Filter MCP provided tools with a specific name.
         * Filtering is applied *before* the name or specification mapping (see {@link #toolNameMapper(BiFunction)}
         * and {@link #toolSpecificationMapper(BiFunction)}) so
         * it should expect raw tool names as received from the MCP server.
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

        /**
         * Provides a presenter for presenting resources via synthetic tools. If none is provided, then
         * resources won't automatically be exposed via tools.
         */
        public McpToolProvider.Builder resourcesAsToolsPresenter(
                McpResourcesAsToolsPresenter resourcesAsToolsPresenter) {
            this.resourcesAsToolsPresenter = resourcesAsToolsPresenter;
            return this;
        }

        /**
         * Defines a mapping function to customize the tool names as they are registered in the tool provider.
         * By default, the tool names are used as-is.
         * It is forbidden to set both a toolNameMapper and a toolSpecificationMapper at the same time.
         * Filtering (see{@link #filter} and {@link #filterToolNames})
         * is applied before name mapping.
         */
        public McpToolProvider.Builder toolNameMapper(BiFunction<McpClient, ToolSpecification, String> toolNameMapper) {
            if (this.toolSpecificationMapper != null) {
                throw new IllegalArgumentException(
                        "It is forbidden to set both a toolNameMapper and a toolSpecificationMapper at the same time.");
            }
            this.toolNameMapper = toolNameMapper;
            return this;
        }

        /**
         * Defines a mapping function to customize the tool specifications as they are registered in the tool provider.
         * By default, the tool descriptions are used as-is.
         *
         * NOTE: When writing the mapping function, don't forget to include the tool arguments as well (these should
         * generally not be changed, perhaps except when adjusting the descriptions of arguments).
         *
         * It is forbidden to set both a toolNameMapper and a toolSpecificationMapper at the same time.
         * Filtering (see{@link #filter} and {@link #filterToolNames})
         * is applied before specification mapping.
         */
        public McpToolProvider.Builder toolSpecificationMapper(
                BiFunction<McpClient, ToolSpecification, ToolSpecification> toolSpecificationMapper) {
            if (this.toolNameMapper != null) {
                throw new IllegalArgumentException(
                        "It is forbidden to set both a toolNameMapper and a toolSpecificationMapper at the same time.");
            }
            this.toolSpecificationMapper = toolSpecificationMapper;
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
