package dev.langchain4j.agentic.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.MissingArgumentException;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;

public abstract class AbstractAgentInvoker implements AgentInvoker, InternalAgent {
    protected final Method method;
    protected final InternalAgent agent;

    protected AbstractAgentInvoker(Method method, InternalAgent agent) {
        this.method = method;
        this.agent = agent;
    }

    @Override
    public Class<?> type() {
        return agent.type();
    }

    @Override
    public String name() {
        return agent.name();
    }

    @Override
    public String agentId() {
        return agent.agentId();
    }

    @Override
    public String description() {
        return agent.description();
    }

    @Override
    public Type outputType() {
        return agent.outputType();
    }

    @Override
    public String outputKey() {
        return agent.outputKey();
    }

    @Override
    public List<AgentArgument> arguments() {
        return agent.arguments();
    }

    @Override
    public List<AgentInstance> subagents() {
        return agent.subagents();
    }

    @Override
    public boolean async() {
        return agent.async();
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
        return agent.listener();
    }

    @Override
    public AgenticSystemTopology topology() {
        return agent.topology();
    }

    @Override
    public Method method() {
        return method;
    }

    @Override
    public AgentInstance parent() {
        return agent.parent();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (AbstractAgentInvoker) obj;
        return Objects.equals(this.method, that.method) &&
                Objects.equals(this.agent, that.agent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, agent);
    }

    @Override
    public String toString() {
        return "MethodAgentInvoker[" +
                "method=" + method + ", " +
                "agentInstance=" + agent + ']';
    }

    @Override
    public void setParent(InternalAgent parent) {
        agent.setParent(parent);
    }

    @Override
    public void appendId(String idSuffix) {
        agent.appendId(idSuffix);
    }
}
