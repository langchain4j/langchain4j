package dev.langchain4j.agentic.agent;

import static dev.langchain4j.agentic.observability.ComposedAgentListener.composeWithInherited;
import static dev.langchain4j.agentic.observability.ComposedAgentListener.listenerOfType;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.afterAgentInvocation;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.agentError;
import static dev.langchain4j.agentic.observability.ListenerNotifierUtil.beforeAgentInvocation;
import static dev.langchain4j.agentic.scope.DefaultAgenticScope.ephemeralAgenticScope;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.invocation.LangChain4jManaged;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.service.decision.DecisionResult;
import dev.langchain4j.service.decision.ThresholdProvider;
import dev.langchain4j.service.decision.internal.DecisionMethod;
import dev.langchain4j.service.decision.internal.DecisionServiceConfig;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The invocation handler of the proxy of a decision agent: it implements the agent method with a call to the
 * decision model, keeps the response of the model for the listeners, and answers the {@link AgentInstance} methods.
 */
@Internal
public class DecisionAgentInvocationHandler implements InvocationHandler, InternalAgent, DecisionResponseAccess {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecisionAgentInvocationHandler.class);

    private final DecisionAgentBuilder<?> builder;
    private final DecisionMethod decisionMethod;
    private final ThresholdProvider thresholdProvider;
    private final DecisionServiceConfig fixedConfig;
    private final Map<Object, DecisionResponse> lastResponses = new ConcurrentHashMap<>();

    private String agentId;
    private InternalAgent parent;
    private AgentListener agentListener;

    DecisionAgentInvocationHandler(DecisionAgentBuilder<?> builder) {
        this.builder = builder;
        this.decisionMethod = builder.decisionMethod;
        this.thresholdProvider = builder.effectiveThresholdProvider();
        this.fixedConfig = builder.decisionModel == null ? null : config(builder.decisionModel);
        this.agentId = builder.name;
        this.agentListener = builder.agentListener;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass == DecisionResponseAccess.class) {
            return switch (method.getName()) {
                case "lastDecisionResponse" -> lastResponses.get(args[0]);
                case "removeLastDecisionResponse" -> {
                    lastResponses.remove(args[0]);
                    yield null;
                }
                default ->
                    throw new UnsupportedOperationException(
                            "Unknown method on DecisionResponseAccess class : " + method.getName());
            };
        }

        if (declaringClass == AgentInstance.class || declaringClass == InternalAgent.class) {
            try {
                return method.invoke(this, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        if (declaringClass == MonitoredAgent.class) {
            return listenerOfType(agentListener, AgentMonitor.class);
        }

        if (declaringClass == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "toString" -> toString();
                case "hashCode" -> System.identityHashCode(this);
                default ->
                    throw new UnsupportedOperationException("Unknown method on Object class : " + method.getName());
            };
        }

        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }

        if (declaringClass == ChatMemoryAccess.class || declaringClass == ChatMessagesAccess.class) {
            // the wrapper of an AI agent, which forwards the methods of these interfaces to the handler
            throw new UnsupportedOperationException("The decision agent '" + name()
                    + "' has no chat memory or chat messages, so it cannot implement "
                    + declaringClass.getSimpleName()
                    + ": a class wrapping it must implement DecisionResponseAccess instead");
        }
        if (!method.equals(builder.agenticMethod)) {
            throw new UnsupportedOperationException("Method '" + method.getName()
                    + "' is not the @Agent method of the decision agent '" + name() + "'");
        }

        AgenticScope agenticScope = LangChain4jManaged.current(AgenticScope.class);
        if (agenticScope != null) {
            return decide(agenticScope, args);
        }
        return invokeStandaloneAgent(args);
    }

    private Object decide(AgenticScope agenticScope, Object[] args) {
        Object memoryId = agenticScope.memoryId();
        try {
            DecisionServiceConfig config = fixedConfig != null ? fixedConfig : config(decisionModel(agenticScope));
            DecisionRequest request = decisionMethod.toRequest(args == null ? new Object[0] : args);
            if (builder.requestParameters != null) {
                request = request.toBuilder()
                        .parameters(builder.requestParameters)
                        .build();
            }
            DecisionResult<Object> result = decisionMethod.invokeWithResponse(config, request);
            lastResponses.put(memoryId, result.response());
            return result.content();
        } catch (RuntimeException e) {
            lastResponses.remove(memoryId);
            throw e;
        }
    }

    private DecisionModel decisionModel(AgenticScope agenticScope) {
        DecisionModel decisionModel = builder.decisionModelProvider.apply(agenticScope);
        if (decisionModel == null) {
            throw new IllegalStateException(
                    "The decision model provider of the decision agent '" + name() + "' returned null");
        }
        return decisionModel;
    }

    private DecisionServiceConfig config(DecisionModel decisionModel) {
        return DecisionServiceConfig.builder()
                .serviceInterface(builder.agentServiceClass)
                .decisionModel(decisionModel)
                .thresholdProvider(thresholdProvider)
                .build();
    }

    private Object invokeStandaloneAgent(Object[] args) {
        LOGGER.warn(
                "Improper invocation of a standalone decision agent outside of an agentic system, consider using DecisionServices instead.");

        DefaultAgenticScope agenticScope = ephemeralAgenticScope();
        LangChain4jManaged.setCurrent(Map.of(AgenticScope.class, agenticScope));
        Map<String, Object> namedArgs = namedArgs(args);
        beforeAgentInvocation(agentListener, agenticScope, this, namedArgs);
        try {
            Object result = decide(agenticScope, args);
            afterAgentInvocation(
                    agentListener,
                    agenticScope,
                    this,
                    namedArgs,
                    result,
                    lastResponses.remove(agenticScope.memoryId()));
            return result;
        } catch (RuntimeException e) {
            AgentInvocationException invocationException =
                    new AgentInvocationException("Failed to invoke agent method: " + builder.agenticMethod, e);
            agentError(agentListener, agenticScope, this, namedArgs, invocationException);
            throw invocationException;
        } finally {
            LangChain4jManaged.removeCurrent();
        }
    }

    private Map<String, Object> namedArgs(Object[] args) {
        Map<String, Object> namedArgs = new LinkedHashMap<>();
        List<AgentArgument> arguments = builder.arguments;
        for (int i = 0; i < arguments.size(); i++) {
            namedArgs.put(arguments.get(i).name(), args == null ? null : args[i]);
        }
        return namedArgs;
    }

    @Override
    public DecisionResponse lastDecisionResponse(Object agenticScopeId) {
        return lastResponses.get(agenticScopeId);
    }

    @Override
    public void removeLastDecisionResponse(Object agenticScopeId) {
        lastResponses.remove(agenticScopeId);
    }

    @Override
    public String toString() {
        return "DecisionAgent<" + builder.agentServiceClass.getSimpleName() + ">";
    }

    @Override
    public void setParent(InternalAgent parent) {
        this.parent = parent;
        if (parent != null) {
            registerInheritedParentListener(parent.listener());
        }
    }

    @Override
    public void registerInheritedParentListener(AgentListener parentListener) {
        if (parentListener != null
                && parentListener.inheritedBySubagents()
                && isNewListener(agentListener, parentListener)) {
            agentListener = composeWithInherited(agentListener, parentListener);
        }
    }

    private static boolean isNewListener(AgentListener currentListener, AgentListener newListener) {
        if (newListener == currentListener) {
            return false;
        }
        if (currentListener instanceof ComposedAgentListener composed) {
            return !composed.contains(newListener);
        }
        return true;
    }

    @Override
    public void appendId(String idSuffix) {
        this.agentId = this.agentId + idSuffix;
    }

    @Override
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    @Override
    public AgentListener listener() {
        return agentListener;
    }

    @Override
    public Class<?> type() {
        return builder.agentServiceClass;
    }

    @Override
    public Class<? extends Planner> plannerType() {
        return null;
    }

    @Override
    public String name() {
        return builder.name;
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public String description() {
        return builder.description;
    }

    @Override
    public Type outputType() {
        return builder.agenticMethod.getGenericReturnType();
    }

    @Override
    public String outputKey() {
        return builder.outputKey;
    }

    @Override
    public boolean async() {
        return builder.async;
    }

    @Override
    public boolean optional() {
        return builder.optional;
    }

    @Override
    public AgentCondition activationCondition() {
        return builder.activationCondition;
    }

    @Override
    public List<AgentArgument> arguments() {
        return builder.arguments;
    }

    @Override
    public AgentInstance parent() {
        return parent;
    }

    @Override
    public List<AgentInstance> subagents() {
        return List.of();
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.DECISION_AGENT;
    }
}
