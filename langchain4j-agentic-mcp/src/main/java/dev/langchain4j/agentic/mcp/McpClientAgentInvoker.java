package dev.langchain4j.agentic.mcp;

import static dev.langchain4j.agentic.internal.AgentUtil.agentInvocationArguments;
import static dev.langchain4j.agentic.internal.AgentUtil.untypedAgentInvocationArguments;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentError;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.internal.AgentInvocationArguments;
import dev.langchain4j.agentic.internal.AgentInvoker;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
            resolveTool(agenticScope);
            for (AgentArgument argument : arguments()) {
                if (!argument.isOptional() && agenticScope.readState(argument.name()) == null) {
                    throw new MissingArgumentException(argument.name());
                }
            }
            return untypedAgentInvocationArguments(agenticScope);
        }
        return agentInvocationArguments(agenticScope, arguments());
    }

    /**
     * Resolves the tool before the invocation, reporting a failure as a failed invocation of the agent, so that it
     * reaches the listeners and the error handler as it does for a typed agent, which resolves it when invoked.
     */
    private void resolveTool(AgenticScope agenticScope) {
        try {
            mcpClientInstance.toolSpecification();
        } catch (RuntimeException e) {
            AgentInvocationException failure =
                    new AgentInvocationException("Failed to resolve the MCP tool of agent '" + name() + "'", e);
            agentError(listener(), agenticScope, this, Map.of(), failure);
            throw failure;
        }
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
        if (parent != null) {
            registerInheritedParentListener(parent.listener());
        }
        checkPlaceholderCollision();
    }

    @Override
    public void registerInheritedParentListener(AgentListener parentListener) {
        mcpClientInstance.registerInheritedParentListener(parentListener);
    }

    @Override
    public void appendId(String idSuffix) {
        this.agentId = this.agentId + idSuffix;
        // also called when an ancestor of this agent is attached to a parent, when a wider system is known
        checkPlaceholderCollision();
    }

    /**
     * An agentic system can contain at most one MCP agent named with the placeholder, otherwise those agents could
     * not be told apart in the events, the reports and the supervisor prompts. Checked whenever this agent or one of
     * its ancestors is attached to a parent, that is at every step of the construction of the system, over all the
     * agents of the topmost parent known at that point, so that the agents of different branches are covered too.
     */
    private void checkPlaceholderCollision() {
        if (parent == null || !DefaultMcpClientBuilder.PLACEHOLDER_NAME.equals(name())) {
            return;
        }
        AgentInstance root = parent;
        while (root.parent() != null) {
            root = root.parent();
        }
        long placeholderAgents = allAgents(root)
                .filter(agent -> DefaultMcpClientBuilder.PLACEHOLDER_NAME.equals(agent.name()))
                .count();
        if (placeholderAgents > 1) {
            throw new AgenticSystemConfigurationException("The agentic system '" + root.name()
                    + "' contains more than one MCP client agent named with the placeholder '"
                    + DefaultMcpClientBuilder.PLACEHOLDER_NAME
                    + "', the name of an untyped MCP agent with neither a name nor a tool name: give each of them "
                    + "a name through McpClientBuilder.name(...) or toolName(...)");
        }
    }

    private static Stream<AgentInstance> allAgents(AgentInstance agent) {
        return Stream.concat(
                Stream.of(agent), agent.subagents().stream().flatMap(McpClientAgentInvoker::allAgents));
    }
}
