package dev.langchain4j.agentic.internal;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.ServiceLoader;

public interface McpService {

    <T> McpClientBuilder<T> mcpBuilder(Object mcpClient, Class<T> agentServiceClass);

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
        public <T> McpClientBuilder<T> mcpBuilder(Object mcpClient, Class<T> agentServiceClass) {
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
