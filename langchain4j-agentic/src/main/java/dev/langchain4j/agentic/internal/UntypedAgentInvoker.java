package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentListenerProvider;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

public final class UntypedAgentInvoker implements AgentInvoker {
    private final Method method;
    private final AgentInstance agentInstance;

    private AgentInstance parent;

    public UntypedAgentInvoker(Method method, AgentInstance agentInstance) {
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
        return method.getGenericReturnType();
    }

    @Override
    public List<AgentInstance> subagents() {
        return agentInstance.subagents();
    }

    @Override
    public String outputKey() {
        return agentInstance.outputKey();
    }

    @Override
    public boolean async() {
        return agentInstance.async();
    }

    @Override
    public List<AgentArgument> arguments() {
        return List.of();
    }

    @Override
    public AgentInvocationArguments toInvocationArguments(AgenticScope agenticScope) {
        return new AgentInvocationArguments(agenticScope.state(), new Object[]{agenticScope.state()});
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
        var that = (UntypedAgentInvoker) obj;
        return Objects.equals(this.method, that.method) &&
                Objects.equals(this.agentInstance, that.agentInstance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, agentInstance);
    }

    @Override
    public String toString() {
        return "UntypedAgentInvoker[" +
                "method=" + method + ", " +
                "agentInstance=" + agentInstance + ']';
    }

}
