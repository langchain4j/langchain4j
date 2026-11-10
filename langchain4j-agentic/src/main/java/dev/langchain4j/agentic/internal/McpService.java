package dev.langchain4j.agentic.internal;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;

public interface McpService {

    /**
     * Creates the builder of an MCP client agent. The supplier returns the {@code McpClient} and is invoked when the
     * client is first needed, on the first invocation of the agent, because creating a client normally connects to
     * the MCP server. Its declared return type is checked now, when the agent is built: it must be an
     * {@code McpClient}, a subtype, or a supertype such as {@code Object}, in which case the instance returned is
     * checked when the client is created.
     *
     * @param mcpClientSupplier the supplier of the client, wrapping the {@code @McpClientSupplier} method
     * @param mcpClientType the declared return type of that method
     * @param agentServiceClass the interface of the agent
     */
    <T> McpClientBuilder<T> mcpBuilder(
            Supplier<Object> mcpClientSupplier, Class<?> mcpClientType, Class<T> agentServiceClass);

    Optional<AgentExecutor> methodToAgentExecutor(InternalAgent mcpClient, Method method);

    static McpService get() {
        return Provider.mcpService;
    }

    class Provider {

        static McpService mcpService = loadMcpService();

        private Provider() { }

        private static McpService loadMcpService() {
            ServiceLoader<McpService> loader =
                    ServiceLoader.load(McpService.class);

            for (McpService service : loader) {
                return service;
            }
            return new DummyMcpService();
        }
    }

    class DummyMcpService implements McpService {

        private DummyMcpService() { }

        @Override
        public <T> McpClientBuilder<T> mcpBuilder(
                Supplier<Object> mcpClientSupplier, Class<?> mcpClientType, Class<T> agentServiceClass) {
            throw noMcpException();
        }

        @Override
        public Optional<AgentExecutor> methodToAgentExecutor(InternalAgent agent, Method method) {
            return Optional.empty();
        }

        private static UnsupportedOperationException noMcpException() {
            return new UnsupportedOperationException(
                    "No MCP service implementation found. Please add 'langchain4j-agentic-mcp' to your dependencies.");
        }
    }
}
