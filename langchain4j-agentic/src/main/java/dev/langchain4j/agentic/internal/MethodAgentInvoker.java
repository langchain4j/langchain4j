package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentListenerProvider;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

public final class MethodAgentInvoker implements AgentInvoker {
    private final Method method;
    private final AgentInstance agentInstance;

    private AgentInstance parent;

    public MethodAgentInvoker(Method method, AgentInstance agentInstance) {
        this.method = method;
        this.agentInstance = agentInstance;
    }

    @Override
    public Class<?> type() {
        return agentInstance.type();
    }

    @Override
    public String name() {
        return agentInstance.name();
    }

    @Override
    public String agentId() {
        return agentInstance.agentId();
    }

    @Override
    public String description() {
        return agentInstance.description();
    }

    @Override
    public Type outputType() {
        return agentInstance.outputType();
    }

    @Override
    public String outputKey() {
        return agentInstance.outputKey();
    }

    @Override
    public List<AgentArgument> arguments() {
        return agentInstance.arguments();
    }

    @Override
    public List<AgentInstance> subagents() {
        return agentInstance.subagents();
    }

    @Override
    public boolean async() {
        return agentInstance.async();
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) throws MissingArgumentException {
        return AgentUtil.agentInvocationArguments(agenticScope, arguments());
    }

    @Override
    public Object invoke(final DefaultAgenticScope agenticScope, final Object agent, final AgentInvocationArguments args) throws AgentInvocationException {
        return AgentInvoker.super.invoke(agenticScope, agent, args);
    }

    @Override
    public AgentListener listener() {
        return ((AgentListenerProvider) agentInstance).listener();
    }

    @Override
    public AgenticSystemTopology topology() {
        return agentInstance.topology();
    }

    @Override
    public Method method() {
        return method;
    }

    @Override
    public void setParent(AgentInstance parent) {
        this.parent = parent;
    }

    @Override
    public AgentInstance parent() {
        return parent;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MethodAgentInvoker) obj;
        return Objects.equals(this.method, that.method) &&
                Objects.equals(this.agentInstance, that.agentInstance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, agentInstance);
    }

    @Override
    public String toString() {
        return "MethodAgentInvoker[" +
                "method=" + method + ", " +
                "agentInstance=" + agentInstance + ']';
    }

}
