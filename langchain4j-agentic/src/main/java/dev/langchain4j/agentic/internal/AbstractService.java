package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.cognisphere.Cognisphere;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;

public abstract class AbstractService<T, S> {

    private static final Function<Cognisphere, Object> DEFAULT_OUTPUT_FUNCTION = cognisphere -> null;
    private static final Consumer<Cognisphere> DEFAULT_INIT_FUNCTION = cognisphere -> { };

    protected final Class<T> agentServiceClass;

    protected Consumer<Cognisphere> beforeCall = DEFAULT_INIT_FUNCTION;

    protected String outputName;
    protected Function<Cognisphere, Object> output = DEFAULT_OUTPUT_FUNCTION;

    private List<AgentExecutor> agentExecutors;

    protected Function<ErrorContext, ErrorRecoveryResult> errorHandler;

    public S beforeCall(Consumer<Cognisphere> beforeCall) {
        this.beforeCall = beforeCall;
        return (S) this;
    }

    public S outputName(String outputName) {
        this.outputName = outputName;
        return (S) this;
    }

    public S output(Function<Cognisphere, Object> output) {
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

    protected AbstractService(Class<T> agentServiceClass) {
        this.agentServiceClass = agentServiceClass;
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
}
