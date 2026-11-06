package dev.langchain4j.agentic.mcp;

import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.untypedAgentInvocationArguments;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.internal.AgentInvocationArguments;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

public class McpClientAgentInvoker implements AgentInvoker {

    private String agentId;

    private final McpClientInstance mcpClientInstance;

    private final Method method;

    private InternalAgent parent;

    public McpClientAgentInvoker(McpClientInstance mcpClientInstance, Method method) {
        this.method = method;
        this.mcpClientInstance = mcpClientInstance;
        this.agentId = name();
    }

    @Override
    public String name() {
        return mcpClientInstance.name();
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public String description() {
        return mcpClientInstance.description();
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
    public AgentCondition activationCondition() {
        return mcpClientInstance.activationCondition();
    }

    @Override
    public List<AgentArgument> arguments() {
        return mcpClientInstance.arguments();
    }

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        if (isUntyped()) {
            // the required inputs of an untyped agent are only known from the input schema of its tool
            mcpClientInstance.toolSpecification();
            for (AgentArgument argument : arguments()) {
                if (!argument.isOptional() && agenticScope.readState(argument.name()) == null) {
                    throw new MissingArgumentException(argument.name());
                }
            }
            return untypedAgentInvocationArguments(agenticScope);
        }
        return agentInvocationArguments(agenticScope, arguments());
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

    /**
     * Gives access to the wrapped MCP client agent, for instance to resolve its tool before the first invocation of
     * the declarative agentic system containing it: {@code subagent.as(McpClientInstance.class).toolSpecification()}.
     */
    @Override
    public <T extends AgentInstance> T as(Class<T> agentInstanceClass) {
        if (agentInstanceClass.isInstance(mcpClientInstance)) {
            return agentInstanceClass.cast(mcpClientInstance);
        }
        return AgentInvoker.super.as(agentInstanceClass);
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
