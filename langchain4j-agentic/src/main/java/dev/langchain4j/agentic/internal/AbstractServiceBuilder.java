package dev.langchain4j.agentic.internal;

import static dev.langchain4j.agentic.internal.AgentUtil.agentsToExecutors;
import static dev.langchain4j.agentic.internal.AgentUtil.buildAgent;
import static dev.langchain4j.agentic.internal.AgentUtil.keyName;
import static dev.langchain4j.agentic.observability.ComposedAgentListener.listenerOfType;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractServiceBuilder<T, S> {

    private static final Consumer<AgenticScope> DEFAULT_INIT_FUNCTION = agenticScope -> {};

    protected final Class<T> agentServiceClass;
    protected final Method agenticMethod;

    protected Consumer<AgenticScope> beforeCall = DEFAULT_INIT_FUNCTION;

    protected String name;
    protected String description;
    protected String outputKey;
    protected Function<AgenticScope, Object> output;

    protected AgentListener agentListener;

    protected final List<AgentExecutor> subagents = new ArrayList<>();

    protected Function<ErrorContext, ErrorRecoveryResult> errorHandler;

    protected Function<InternalAgent, Object> agentInstanceFactory;

    protected Executor executor;

    protected AbstractServiceBuilder(Class<T> agentServiceClass, Method agenticMethod) {
        this.agentServiceClass = agentServiceClass;
        this.agenticMethod = agenticMethod;
        initService(agenticMethod);
    }

    private void initService(Method agenticMethod) {
        if (agenticMethod == null) {
            this.name = this.serviceType();
            return;
        }
        Agent agent = agenticMethod.getAnnotation(Agent.class);
        if (agent == null) {
            return;
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
        this.outputKey = AgentUtil.outputKey(agent.outputKey(), agent.typedOutputKey());
    }

    Type agentReturnType() {
        return agenticMethod == null ? Object.class : agenticMethod.getGenericReturnType();
    }

    /**
     * Sets a callback to execute before the agent is invoked, allowing initialization of the
     * {@link AgenticScope}.
     *
     * @param beforeCall the callback to run before each agent invocation
     * @return {@code this}
     */
    public S beforeCall(Consumer<AgenticScope> beforeCall) {
        this.beforeCall = beforeCall;
        return (S) this;
    }

    /**
     * Sets the name of the agent, used for identification in multi-agent workflows.
     *
     * @param name the agent name
     * @return {@code this}
     */
    public S name(String name) {
        this.name = name;
        return (S) this;
    }

    /**
     * Sets a human-readable description of the agent's purpose or capabilities.
     *
     * @param description the agent description
     * @return {@code this}
     */
    public S description(String description) {
        this.description = description;
        return (S) this;
    }

    /**
     * Sets the key under which the agent's output is stored in the {@link AgenticScope}.
     *
     * @param outputKey the output key name
     * @return {@code this}
     */
    public S outputKey(String outputKey) {
        this.outputKey = outputKey;
        return (S) this;
    }

    /**
     * Sets the output key using a {@link TypedKey} class, deriving the key name from the class.
     *
     * @param outputKey the typed key class
     * @return {@code this}
     */
    public S outputKey(Class<? extends TypedKey<?>> outputKey) {
        return outputKey(keyName(outputKey));
    }

    /**
     * Sets a function to extract the agent's output value from the {@link AgenticScope}.
     *
     * @param output the output extraction function
     * @return {@code this}
     */
    public S output(Function<AgenticScope, Object> output) {
        this.output = output;
        return (S) this;
    }

    /**
     * Registers sub-agents that this agent can delegate work to.
     *
     * @param agents the sub-agents (varargs)
     * @return {@code this}
     */
    public S subAgents(Object... agents) {
        return subAgents(List.of(agents));
    }

    /**
     * Registers sub-agents that this agent can delegate work to.
     *
     * @param agents the collection of sub-agents
     * @return {@code this}
     */
    public S subAgents(Collection<?> agents) {
        addSubagents(agentsToExecutors(agents));
        return (S) this;
    }

    /**
     * Sets a handler to recover from errors that occur during agent execution.
     *
     * @param errorHandler the error recovery function
     * @return {@code this}
     */
    public S errorHandler(Function<ErrorContext, ErrorRecoveryResult> errorHandler) {
        this.errorHandler = errorHandler;
        return (S) this;
    }

    /**
     * Registers an {@link AgentListener} to observe agent lifecycle events. Multiple listeners can
     * be added; they are composed automatically.
     *
     * @param agentListener the listener to register
     * @return {@code this}
     */
    public S listener(AgentListener agentListener) {
        if (this.agentListener == null) {
            this.agentListener = agentListener;
        } else if (this.agentListener instanceof ComposedAgentListener composed) {
            composed.addListener(agentListener);
        } else {
            this.agentListener = new ComposedAgentListener(this.agentListener, agentListener);
        }
        return (S) this;
    }

    /**
     * Sets a factory function used to wrap the internal agent handler into a custom instance type.
     *
     * @param factory the factory function
     * @return {@code this}
     */
    public S agentInstanceFactory(Function<InternalAgent, Object> factory) {
        this.agentInstanceFactory = factory;
        return (S) this;
    }

    /**
     * Sets the {@link Executor} used for asynchronous agent operations.
     *
     * @param executor the executor
     * @return {@code this}
     */
    public S executor(Executor executor) {
        this.executor = executor;
        return (S) this;
    }

    private void addSubagents(List<AgentExecutor> agents) {
        this.subagents.addAll(agents);
    }

    /**
     * Builds the agent service using the supplied {@link Planner}, wiring in monitoring if the
     * service class implements {@link MonitoredAgent}.
     *
     * @param plannerSupplier supplier of the planner used to orchestrate agent execution
     * @return the built agent service instance
     */
    public T build(Supplier<Planner> plannerSupplier) {
        AgentMonitor monitor = listenerOfType(agentListener, AgentMonitor.class);
        if (MonitoredAgent.class.isAssignableFrom(agentServiceClass) && monitor == null) {
            monitor = new AgentMonitor();
            listener(monitor);
        }
        AgentInstance agent = (AgentInstance) build(new PlannerBasedInvocationHandler(this, plannerSupplier));
        if (monitor != null) {
            monitor.setRootAgent(agent);
        }
        return (T) agent;
    }

    /**
     * Builds the agent service using the provided {@link InvocationHandler}.
     *
     * @param invocationHandler the invocation handler that processes agent method calls
     * @return the built agent service instance
     */
    public T build(InvocationHandler invocationHandler) {
        if (agentInstanceFactory != null) {
            return (T) agentInstanceFactory.apply((InternalAgent) invocationHandler);
        }
        return buildAgent(agentServiceClass, invocationHandler);
    }

    public abstract String serviceType();
}
