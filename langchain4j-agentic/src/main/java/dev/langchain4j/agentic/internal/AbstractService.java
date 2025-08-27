package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

public abstract class AbstractService<T, S> {

    private static final Function<AgenticScope, Object> DEFAULT_OUTPUT_FUNCTION = agenticScope -> null;
    private static final Consumer<AgenticScope> DEFAULT_INIT_FUNCTION = agenticScope -> { };

    protected final Class<T> agentServiceClass;

    protected Consumer<AgenticScope> beforeCall = DEFAULT_INIT_FUNCTION;

    protected String name;
    protected String description;
    protected String outputName;
    protected Function<AgenticScope, Object> output = DEFAULT_OUTPUT_FUNCTION;

    private List<AgentExecutor> agentExecutors;

    protected Function<ErrorContext, ErrorRecoveryResult> errorHandler;

    protected AbstractService(Class<T> agentServiceClass, Method agenticMethod) {
        this.agentServiceClass = agentServiceClass;
        initService(agenticMethod);
    }

    private void initService(Method agenticMethod) {
        if (agenticMethod == null) {
            this.name = "UntypedAgent#" + UUID.randomUUID();
            return;
        }
        Agent agent = agenticMethod.getAnnotation(Agent.class);
        if (agent == null) {
            throw new IllegalArgumentException("Method " + agenticMethod + " is not annotated with @Agent");
        }

        if (!isNullOrBlank(agent.name())) {
            this.name = agent.name();
        } else {
            this.name = agenticMethod.getName();
        }
        if (!isNullOrBlank(agent.description())) {
            this.description = agent.description();
        } else if (!isNullOrBlank(agent.value())) {
            this.description = agent.value();
        }
        if (!isNullOrBlank(agent.outputName())) {
            this.outputName = agent.outputName();
        }
    }

    public S beforeCall(Consumer<AgenticScope> beforeCall) {
        this.beforeCall = beforeCall;
        return (S) this;
    }

    public S name(String name) {
        this.name = name;
        return (S) this;
    }

    public S description(String description) {
        this.description = description;
        return (S) this;
    }

    public S outputName(String outputName) {
        this.outputName = outputName;
        return (S) this;
    }

    public S output(Function<AgenticScope, Object> output) {
        this.output = output;
        return (S) this;
    }

    public S subAgents(Object... agents) {
        return subAgents(agentsToExecutors(agents));
    }

    public S subAgents(List<AgentExecutor> agentExecutors) {
        addAgentExecutors(agentExecutors);
        return (S) this;
    }

    public S errorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler) {
        this.errorHandler = errorHandler;
        return (S) this;
    }

    protected List<AgentExecutor> agentExecutors() {
        return agentExecutors != null ? agentExecutors : List.of();
    }

    private void addAgentExecutors(List<AgentExecutor> agents) {
        if (this.agentExecutors == null) {
            this.agentExecutors = new ArrayList<>();
        }
        this.agentExecutors.addAll(agents);
    }

    protected boolean hasOutputFunction() {
        return this.output != DEFAULT_OUTPUT_FUNCTION;
    }
}
