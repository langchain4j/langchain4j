package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.internal.McpClientBuilder;
import dev.langchain4j.agentic.internal.McpService;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.mcp.client.McpClient;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;

public class DefaultMcpService implements McpService {

    @Override
    public <T> McpClientBuilder<T> mcpBuilder(
            Supplier<Object> mcpClientSupplier, Class<?> mcpClientType, Class<T> agentServiceClass) {
        // A supplier method declared to return something that cannot be an McpClient is a configuration error
        // reported now, at build; one declared with a supertype such as Object is checked when the client is created
        if (!McpClient.class.isAssignableFrom(mcpClientType) && !mcpClientType.isAssignableFrom(McpClient.class)) {
            throw new AgenticSystemConfigurationException("The @McpClientSupplier method of "
                    + agentServiceClass.getName() + " is declared to return " + mcpClientType.getName()
                    + " instead of an McpClient");
        }
        return new DefaultMcpClientBuilder<>(
                () -> mcpClient(mcpClientSupplier.get(), agentServiceClass), agentServiceClass);
    }

    private static McpClient mcpClient(Object mcpClient, Class<?> agentServiceClass) {
        if (mcpClient == null || mcpClient instanceof McpClient) {
            return (McpClient) mcpClient;
        }
        throw new AgenticSystemConfigurationException("The @McpClientSupplier method of " + agentServiceClass.getName()
                + " returned an instance of " + mcpClient.getClass().getName() + " instead of an McpClient");
    }

    @Override
    public Optional<AgentExecutor> methodToAgentExecutor(InternalAgent agent, Method method) {
        if (agent instanceof McpClientInstance mcpAgent) {
            Optional<AgentExecutor> mcpAgentExecutor = getAnnotatedMethod(method, Agent.class)
                    .map(agentMethod -> new AgentExecutor(new McpClientAgentInvoker(mcpAgent, agentMethod), mcpAgent));
            if (mcpAgentExecutor.isEmpty()) {
                mcpAgentExecutor = getAnnotatedMethod(method, McpClientAgent.class)
                        .map(agentMethod -> new AgentExecutor(new McpClientAgentInvoker(mcpAgent, agentMethod), mcpAgent));
            }
            return mcpAgentExecutor;
        }
        return Optional.empty();
    }
}
