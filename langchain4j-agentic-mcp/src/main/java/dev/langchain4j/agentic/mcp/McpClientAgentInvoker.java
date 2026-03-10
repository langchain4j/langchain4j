package dev.langchain4j.agentic.mcp;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.internal.AgentInvocationArguments;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class McpClientAgentInvoker implements AgentInvoker {

    private String agentId;
    private final String[] inputKeys;

    private final McpClientInstance mcpClientInstance;

    private final String toolName;
    private final String toolDescription;
    private final Method method;

    private InternalAgent parent;

    public McpClientAgentInvoker(McpClientInstance mcpClientInstance, Method method) {
        this.method = method;
        this.mcpClientInstance = mcpClientInstance;
        this.toolName = mcpClientInstance.toolName();
        this.toolDescription = mcpClientInstance.toolDescription();
        this.agentId = name();
        this.inputKeys = inputKeys(mcpClientInstance);
    }

    private String[] inputKeys(McpClientInstance mcpClientInstance) {
        return isUntyped()
                ? mcpClientInstance.inputKeys()
                : Stream.of(method.getParameters())
                        .map(AgentInvoker::parameterName)
                        .toArray(String[]::new);
    }

    @Override
    public String name() {
        return toolName;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public String description() {
        return toolDescription;
    }

    @Override
    public Class<?> type() {
        return Object.class;
    }

    @Override
    public Class<? extends Planner> plannerType() {
        return null;
    }

    @Override
    public Type outputType() {
        return Object.class;
    }

    @Override
    public String outputKey() {
        return mcpClientInstance.outputKey();
    }

    @Override
    public boolean async() {
        return mcpClientInstance.async();
    }

    @Override
    public Method method() {
        return method;
    }

    @Override
    public List<AgentArgument> arguments() {
        return Stream.of(inputKeys).map(input -> new AgentArgument(Object.class, input)).toList();
    }

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        return isUntyped()
                ? new AgentInvocationArguments(agenticScope.state(), new Object[] {agenticScope.state()})
                : agentInvocationArguments(agenticScope);
    }

    private AgentInvocationArguments agentInvocationArguments(AgenticScope agenticScope) {
        Map<String, Object> namedArgs = new HashMap<>();
        Object[] positionalArgs = new Object[inputKeys.length];

        int i = 0;
        for (String argName : inputKeys) {
            Object argValue = agenticScope.readState(argName);
            positionalArgs[i++] = argValue;
            namedArgs.put(argName, argValue);
        }
        return new AgentInvocationArguments(namedArgs, positionalArgs);
    }

    private boolean isUntyped() {
        return method.getDeclaringClass() == UntypedAgent.class;
    }

    @Override
    public AgentListener listener() {
        return mcpClientInstance.listener();
    }

    @Override
    public AgenticSystemTopology topology() {
        return mcpClientInstance.topology();
    }

    @Override
    public AgentInstance parent() {
        return parent;
    }

    @Override
    public void setParent(InternalAgent parent) {
        this.parent = parent;
    }

    @Override
    public void registerInheritedParentListener(AgentListener parentListener) {
        mcpClientInstance.registerInheritedParentListener(parentListener);
    }

    @Override
    public void appendId(String idSuffix) {
        this.agentId = this.agentId + idSuffix;
    }
}
