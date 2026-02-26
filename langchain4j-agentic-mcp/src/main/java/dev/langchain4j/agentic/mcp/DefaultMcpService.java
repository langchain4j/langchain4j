package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.internal.McpClientBuilder;
import dev.langchain4j.agentic.internal.McpService;
import dev.langchain4j.mcp.client.McpClient;
import java.lang.reflect.Method;
import java.util.Optional;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;

public class DefaultMcpService implements McpService {

    @Override
    public <T> McpClientBuilder<T> mcpBuilder(Object mcpClient, Class<T> agentServiceClass) {
        return new DefaultMcpClientBuilder<>((McpClient) mcpClient, agentServiceClass);
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
