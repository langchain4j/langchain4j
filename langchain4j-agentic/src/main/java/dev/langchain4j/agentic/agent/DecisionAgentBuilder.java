package dev.langchain4j.agentic.agent;

import static dev.langchain4j.agentic.internal.AgentUtil.argumentsFromMethod;
import static dev.langchain4j.agentic.internal.AgentUtil.keyName;
import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;
import static dev.langchain4j.agentic.observability.ComposedAgentListener.listenerOfType;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.Internal;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.DecisionModelSupplier;
import dev.langchain4j.agentic.declarative.DecisionThresholdSupplier;
import dev.langchain4j.agentic.declarative.DeclarativeUtil;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.agentic.internal.AgentUtil;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentMonitor;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.observability.ComposedAgentListener;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.ParameterNameResolver;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.decision.DecisionServices;
import dev.langchain4j.service.decision.ThresholdProvider;
import dev.langchain4j.service.decision.internal.DecisionMethod;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builds a <em>decision agent</em>: an agent of an agentic system whose implementation is a single call to a
 * {@link DecisionModel} instead of a chat model. It is defined by a Decision Service interface (see
 * {@link DecisionServices}) whose method also carries the
 * {@link Agent @Agent} annotation:
 * <pre>{@code
 * public interface CategoryRouter {
 *
 *     @Decide("Which kind of expert should handle this request?")
 *     @Agent(value = "Categorizes a user request", outputKey = "category")
 *     RequestCategory classify(@V("request") String request);
 * }
 *
 * CategoryRouter router = AgenticServices.decisionAgentBuilder(CategoryRouter.class)
 *         .decisionModel(decisionModel)
 *         .build();
 * }</pre>
 * The arguments of the method are read from the {@link AgenticScope} by name, like for every agent, and sent to
 * the decision model as the input of the decision (objects are converted to maps, as in Decision Services); the
 * return type determines the questions: {@code boolean}, an enum, {@code YesNoAnswer}, {@code Choice<enum>},
 * {@code Scale<enum>}, an object whose fields are of those types, optionally wrapped in {@code DecisionResult}.
 * The answer is written as it is to the output key, so that the activation conditions, the exit conditions and the
 * agents that follow can read it, including its probabilities. {@code CompletableFuture} return types are not
 * supported: use {@link #async(boolean)} to run the agent asynchronously.
 * <p>
 * Each invocation makes one blocking call to the decision model. The response of the model, with the probabilities
 * of its answers, its model name and its token usage, is reported to the {@link AgentListener}s through
 * {@link AgentResponse#decisionResponse()} and recorded by the
 * {@link AgentMonitor}. A decision agent has no chat memory, tools, retrieval or guardrails.
 *
 * @param <T> the decision agent interface
 * @since 1.23.0
 */
@Experimental
public class DecisionAgentBuilder<T> {

    final Class<T> agentServiceClass;
    final Method agenticMethod;
    final DecisionMethod decisionMethod;
    final List<AgentArgument> arguments;

    String name;
    String description;
    String outputKey;
    boolean async;
    boolean optional;
    AgentCondition activationCondition;

    DecisionModel decisionModel;
    Function<AgenticScope, DecisionModel> decisionModelProvider;
    ThresholdProvider thresholdProvider;
    Double threshold;
    DecisionRequestParameters requestParameters;
    AgentListener agentListener;
    Function<InternalAgent, Object> agentInstanceFactory;

    /**
     * Creates a builder for the given decision agent interface, applying its declarative configuration
     * ({@link DecisionModelSupplier @DecisionModelSupplier},
     * {@link DecisionThresholdSupplier @DecisionThresholdSupplier},
     * {@link ActivationCondition @ActivationCondition} and
     * {@link AgentListenerSupplier @AgentListenerSupplier}) when present.
     *
     * @throws IllegalArgumentException if the interface cannot be a decision agent
     */
    public DecisionAgentBuilder(Class<T> agentServiceClass) {
        this(agentServiceClass, true);
    }

    @Internal
    public static <T> DecisionAgentBuilder<T> withoutDeclarativeConfiguration(Class<T> agentServiceClass) {
        return new DecisionAgentBuilder<>(agentServiceClass, false);
    }

    private DecisionAgentBuilder(Class<T> agentServiceClass, boolean configureDeclarativeAgent) {
        this.agentServiceClass = ensureNotNull(agentServiceClass, "agentServiceClass");
        if (!agentServiceClass.isInterface()) {
            throw new IllegalArgumentException("A decision agent must be defined by an interface, but "
                    + agentServiceClass.getName() + " is a class");
        }
        this.agenticMethod = validateAgentClass(agentServiceClass);
        validateMethod(agentServiceClass, agenticMethod);
        this.decisionMethod = DecisionMethod.from(agenticMethod);
        if (decisionMethod.isAsync()) {
            throw new IllegalArgumentException(
                    ("Method '%s' of the decision agent %s returns a %s, which is not supported: "
                                    + "declare the type of the result directly and make the agent asynchronous with async(true) "
                                    + "or @Agent(async = true)")
                            .formatted(
                                    agenticMethod.getName(),
                                    agentServiceClass.getName(),
                                    agenticMethod.getReturnType().getSimpleName()));
        }
        this.arguments = argumentsFromMethod(agenticMethod);

        Agent agent = agenticMethod.getAnnotation(Agent.class);
        this.name = !isNullOrBlank(agent.name()) ? agent.name() : agenticMethod.getName();
        if (!isNullOrBlank(agent.description())) {
            this.description = agent.description();
        } else if (!isNullOrBlank(agent.value())) {
            this.description = agent.value();
        }
        this.outputKey = AgentUtil.outputKey(agent.outputKey(), agent.typedOutputKey());
        this.async = agent.async();
        this.optional = agent.optional();

        if (configureDeclarativeAgent) {
            DeclarativeUtil.configureDecisionAgent(agentServiceClass, this);
        }
    }

    private static void validateMethod(Class<?> agentServiceClass, Method method) {
        if (method.isAnnotationPresent(UserMessage.class) || method.isAnnotationPresent(SystemMessage.class)) {
            throw new IllegalArgumentException(("Method '%s' of the decision agent %s must not be annotated with "
                            + "@UserMessage or @SystemMessage: a decision agent has no prompt, its question is the one of @Decide")
                    .formatted(method.getName(), agentServiceClass.getName()));
        }
        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(MemoryId.class)) {
                throw new IllegalArgumentException(
                        ("Parameter '%s' of method '%s' of the decision agent %s is annotated "
                                        + "with @MemoryId, which is not supported: a decision agent has no chat memory")
                                .formatted(parameterName(parameter), method.getName(), agentServiceClass.getName()));
            }
            if (AgenticScope.class.isAssignableFrom(parameter.getType())) {
                throw new IllegalArgumentException(("Parameter '%s' of method '%s' of the decision agent %s has type "
                                + "AgenticScope, which is not supported: the arguments of a decision agent are read from "
                                + "the AgenticScope by name and sent to the decision model")
                        .formatted(parameterName(parameter), method.getName(), agentServiceClass.getName()));
            }
            if (DecisionRequestParameters.class.isAssignableFrom(parameter.getType())) {
                throw new IllegalArgumentException(("Parameter '%s' of method '%s' of the decision agent %s has type "
                                + "DecisionRequestParameters, which is not supported: set the request parameters with "
                                + "requestParameters(...) on the builder, or as default parameters of the decision model")
                        .formatted(parameterName(parameter), method.getName(), agentServiceClass.getName()));
            }
        }
    }

    private static String parameterName(Parameter parameter) {
        String name = ParameterNameResolver.name(parameter);
        return name != null ? name : parameter.getName();
    }

    /**
     * Sets the {@link DecisionModel} answering the question(s) of this agent.
     */
    public DecisionAgentBuilder<T> decisionModel(DecisionModel decisionModel) {
        this.decisionModel = decisionModel;
        return this;
    }

    /**
     * Sets a provider that resolves the {@link DecisionModel} from the {@link AgenticScope} before each invocation,
     * for example to pick a model depending on the state of the scope. Alternative to
     * {@link #decisionModel(DecisionModel)}.
     */
    public DecisionAgentBuilder<T> decisionModelProvider(Function<AgenticScope, DecisionModel> decisionModelProvider) {
        this.decisionModelProvider = decisionModelProvider;
        return this;
    }

    /**
     * Sets the provider of the threshold of the {@code boolean} answers of this agent: the answer is {@code true}
     * when the probability of "yes" is at least the threshold. The provider is called at every invocation, so the
     * threshold can come from configuration that changes at runtime. Without it, and without {@link #threshold},
     * the threshold is 0.5. Mutually exclusive with {@link #threshold(double)}. Like a fixed threshold, it is only
     * allowed when the agent has a {@code boolean} answer, otherwise {@link #build()} fails.
     */
    public DecisionAgentBuilder<T> thresholdProvider(ThresholdProvider thresholdProvider) {
        this.thresholdProvider = thresholdProvider;
        return this;
    }

    /**
     * Sets a fixed threshold for the {@code boolean} answers of this agent: the answer is {@code true} when the
     * probability of "yes" is at least the threshold. Mutually exclusive with
     * {@link #thresholdProvider(ThresholdProvider)}. Thresholds are specific to a decision model and to its version.
     * A threshold is only allowed when the agent has a {@code boolean} answer, otherwise {@link #build()} fails: a
     * {@code YesNoAnswer}, a {@code Choice} or a {@code Scale} carries the probabilities to apply a threshold to.
     *
     * @param threshold a value between 0 and 1
     * @throws IllegalArgumentException if the threshold is not between 0 and 1
     */
    public DecisionAgentBuilder<T> threshold(double threshold) {
        if (!(threshold >= 0 && threshold <= 1)) {
            throw new IllegalArgumentException(
                    "The threshold of the decision agent '" + name + "' must be between 0 and 1, but was " + threshold);
        }
        this.threshold = threshold;
        return this;
    }

    /**
     * Sets the parameters of every decision request of this agent, such as the model name, overriding the defaults
     * of the decision model.
     */
    public DecisionAgentBuilder<T> requestParameters(DecisionRequestParameters requestParameters) {
        this.requestParameters = requestParameters;
        return this;
    }

    /**
     * Sets the name of this agent, overriding the one of {@link Agent @Agent} or the method name.
     */
    public DecisionAgentBuilder<T> name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the description of this agent, overriding the one of {@link Agent @Agent}. It is what a supervisor reads
     * to decide when to invoke this agent, so it should say which question the agent answers.
     */
    public DecisionAgentBuilder<T> description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Sets the key of the {@link AgenticScope} where the answer of this agent is written, overriding the one of
     * {@link Agent @Agent}.
     */
    public DecisionAgentBuilder<T> outputKey(String outputKey) {
        this.outputKey = outputKey;
        return this;
    }

    /**
     * Sets the output key using a {@link TypedKey} class for type-safe scope access.
     */
    public DecisionAgentBuilder<T> outputKey(Class<? extends TypedKey<?>> outputKey) {
        return outputKey(keyName(outputKey));
    }

    /**
     * Controls whether this agent executes asynchronously within an agentic system: the decision call then runs
     * on the executor used for every asynchronous agent, and the agents reading its output wait for it.
     */
    public DecisionAgentBuilder<T> async(boolean async) {
        this.async = async;
        return this;
    }

    /**
     * Controls whether this agent is optional: when it is, it is skipped instead of failing when one of its
     * arguments is missing from the {@link AgenticScope}.
     */
    public DecisionAgentBuilder<T> optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    /**
     * Sets the condition under which this agent runs when it is invoked as a subagent of an agentic system. When the
     * condition is false the agent is skipped, without writing anything to the {@link AgenticScope}. The condition
     * is ignored when this agent is invoked directly. It can be evaluated more than once, so it must be free of side
     * effects.
     */
    public DecisionAgentBuilder<T> activationCondition(Predicate<AgenticScope> activationCondition) {
        return activationCondition("<unknown>", activationCondition);
    }

    /**
     * Sets the condition under which this agent runs when it is invoked as a subagent of an agentic system, with a
     * human-readable description used in logs, listeners and reports.
     *
     * @see #activationCondition(Predicate)
     */
    public DecisionAgentBuilder<T> activationCondition(
            String description, Predicate<AgenticScope> activationCondition) {
        this.activationCondition = new AgentCondition(description, activationCondition);
        return this;
    }

    /**
     * Adds a listener of the invocations of this agent, composed with the ones already registered.
     */
    public DecisionAgentBuilder<T> listener(AgentListener agentListener) {
        this.agentListener = ComposedAgentListener.compose(this.agentListener, agentListener);
        return this;
    }

    @Internal
    public DecisionAgentBuilder<T> agentInstanceFactory(Function<InternalAgent, Object> factory) {
        this.agentInstanceFactory = factory;
        return this;
    }

    /**
     * Whether a decision model, or a provider of it, has been set.
     */
    @Internal
    public boolean hasDecisionModel() {
        return decisionModel != null || decisionModelProvider != null;
    }

    ThresholdProvider effectiveThresholdProvider() {
        if (thresholdProvider != null) {
            return thresholdProvider;
        }
        if (threshold != null) {
            double fixedThreshold = threshold;
            return context -> fixedThreshold;
        }
        return null;
    }

    /**
     * Builds the decision agent.
     *
     * @throws IllegalArgumentException if neither or both of the decision model and its provider are set, if both
     * a threshold and a threshold provider are set, or if one of them is set on an agent without a
     * {@code boolean} answer
     */
    public T build() {
        if ((decisionModel == null) == (decisionModelProvider == null)) {
            throw new IllegalArgumentException(
                    "One and only one of decisionModel and decisionModelProvider must be set for the decision agent '"
                            + name + "'");
        }
        if (threshold != null && thresholdProvider != null) {
            throw new IllegalArgumentException(
                    "Only one of threshold and thresholdProvider can be set for the decision agent '" + name + "'");
        }
        if ((threshold != null || thresholdProvider != null) && !decisionMethod.hasBooleanAnswer()) {
            throw new IllegalArgumentException("The decision agent '" + name
                    + "' has no boolean answer, so a threshold would never be used: apply it to the probabilities"
                    + " of a YesNoAnswer, Choice or Scale answer instead, for instance with"
                    + " YesNoAnswer.isYes(threshold)");
        }

        AgentMonitor monitor = listenerOfType(agentListener, AgentMonitor.class);
        if (MonitoredAgent.class.isAssignableFrom(agentServiceClass) && monitor == null) {
            monitor = new AgentMonitor();
            listener(monitor);
        }

        DecisionAgentInvocationHandler handler = new DecisionAgentInvocationHandler(this);
        AgentInstance agent;
        if (agentInstanceFactory != null) {
            agent = (AgentInstance) agentInstanceFactory.apply(handler);
        } else {
            agent = (AgentInstance) Proxy.newProxyInstance(
                    agentServiceClass.getClassLoader(), interfacesToImplement(agentServiceClass), handler);
        }

        if (monitor != null) {
            monitor.setRootAgent(agent);
        }
        return (T) agent;
    }

    private static Class<?>[] interfacesToImplement(Class<?> agentServiceClass) {
        return new Class<?>[] {agentServiceClass, InternalAgent.class, DecisionResponseAccess.class};
    }
}
