package dev.langchain4j.agentic.patterns.decisionrouter;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agentic.internal.DelayedResponse;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A router planner that lets a {@link DecisionModel} choose which subagents handle a request. The input of the
 * decision is made of the arguments of the router agent itself, read from the {@link AgenticScope}.
 * <p>
 * Without an activation threshold, each subagent is an option of a single choice question, described by its name and
 * description, and the planner invokes only the most probable subagent and returns its output.
 * <p>
 * With an activation threshold, the planner asks instead one yes/no question per subagent, all in the same request
 * ("Should the agent '...' handle this request?"), invokes in parallel all the subagents whose probability of "yes"
 * is at least the threshold, and returns a map from the name of each invoked subagent to its output, so the method of
 * the router agent must return a {@code Map}, which is checked when the router is invoked. These probabilities are
 * independent of each other: several subagents can reach a high threshold, adding a subagent does not change the
 * probabilities of the others, and when no subagent reaches the threshold none is invoked and the result is an empty
 * map.
 */
@Experimental
public class DecisionRouterPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(DecisionRouterPlanner.class);

    static final String QUESTION_NAME = "decision-agent";
    static final String QUESTION_TEXT = "Which agent is best suited to handle this request?";

    private static final String DECISION_ROUTER_KEY_PREFIX = "decision-router-";
    private static final String ACTIVATED_STATE_KEY = DECISION_ROUTER_KEY_PREFIX + "activated";
    private static final String OUTPUTS_STATE_KEY = DECISION_ROUTER_KEY_PREFIX + "outputs";
    private static final String DEFERRED_STATE_KEY = DECISION_ROUTER_KEY_PREFIX + "deferred";

    private final DecisionModel decisionModel;
    private final Double activationThreshold;

    private List<String> inputKeys;
    private final Map<String, AgentInstance> routes = new LinkedHashMap<>();

    // a single choice question among the subagents without an activation threshold, one yes/no question per subagent
    // (named after it) with an activation threshold
    private final Map<String, Question> questions = new LinkedHashMap<>();

    // persisted to resume after a suspension or a crash: the names of the activated agents, the outputs of those
    // already invoked, and the names of those whose output was still pending, like an agent waiting for a human
    private List<String> activated;
    private final Map<String, Object> outputs = new HashMap<>();
    private final Set<String> deferred = new HashSet<>();

    /**
     * Creates a router invoking only the subagent that the decision model considers the most probable.
     * <p>
     * When the router is invoked, before asking the decision model, it fails with an
     * {@link IllegalArgumentException} if the router agent is not a typed agent interface with at least one argument
     * to send to the decision model, or if two subagents have the same name.
     *
     * @param decisionModel the decision model choosing the subagent to invoke
     * @throws IllegalArgumentException if {@code decisionModel} is {@code null}
     */
    public DecisionRouterPlanner(DecisionModel decisionModel) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        this.activationThreshold = null;
    }

    /**
     * Creates a router asking the decision model, for each subagent, whether it should handle the request, and
     * invoking in parallel all the subagents whose probability of "yes" is at least the given threshold.
     * <p>
     * The result of the router is then a map from the name of each invoked subagent to its output, so the method of
     * the router agent must return a {@code Map} (or a {@code ResultWithAgenticScope} of a {@code Map}). When the
     * router is invoked, before asking the decision model, it fails with an {@link IllegalArgumentException} if it
     * does not, if the router agent is not a typed agent interface with at least one argument to send to the
     * decision model, or if two subagents have the same name.
     *
     * @param decisionModel the decision model choosing the subagents to invoke
     * @param activationThreshold the minimum probability of "yes" for a subagent to be invoked, strictly between 0
     *        and 1
     * @throws IllegalArgumentException if {@code decisionModel} is {@code null}, or if {@code activationThreshold}
     *         is not strictly between 0 and 1
     */
    public DecisionRouterPlanner(DecisionModel decisionModel, double activationThreshold) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        this.activationThreshold = validThreshold(activationThreshold);
    }

    private static double validThreshold(double threshold) {
        if (threshold <= 0.0 || threshold >= 1.0) {
            throw illegalArgument(
                    "Activation threshold must be greater than 0.0 and lesser than 1.0, but is: %s", threshold);
        }
        return threshold;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        // framework-provided arguments, like @MemoryId, are not part of the request
        this.inputKeys = initPlanningContext.plannerAgent().arguments().stream()
                .map(AgentArgument::name)
                .filter(name -> !name.startsWith("@"))
                .toList();
        if (inputKeys.isEmpty()) {
            throw new IllegalArgumentException("DecisionRouterPlanner requires a typed agent interface whose method "
                    + "arguments are the input of the routing decision");
        }
        if (activationThreshold != null) {
            ensureMapResult(initPlanningContext.plannerAgent().outputType());
        }

        for (AgentInstance agent : initPlanningContext.subagents()) {
            if (routes.put(agent.name(), agent) != null) {
                throw new IllegalArgumentException("DecisionRouterPlanner requires subagents with distinct names, "
                        + "but more than one is named '" + agent.name() + "'");
            }
        }

        if (activationThreshold == null) {
            ChoiceQuestion.Builder choice = ChoiceQuestion.builder().text(QUESTION_TEXT);
            for (AgentInstance agent : routes.values()) {
                if (isNullOrBlank(agent.description())) {
                    choice.option(agent.name());
                } else {
                    choice.option(agent.name(), agent.description());
                }
            }
            questions.put(QUESTION_NAME, choice.build());
        } else {
            routes.values().forEach(agent -> questions.put(agent.name(), relevanceQuestion(agent)));
        }
    }

    private static YesNoQuestion relevanceQuestion(AgentInstance agent) {
        String description = isNullOrBlank(agent.description()) ? "" : " (" + agent.description() + ")";
        return YesNoQuestion.of("Should the agent '" + agent.name() + "'" + description + " handle this request?");
    }

    private void ensureMapResult(Type outputType) {
        // the declared type of the result, without the ResultWithAgenticScope wrapper
        Type resultType = outputType instanceof ParameterizedType parameterized
                        && parameterized.getRawType() == ResultWithAgenticScope.class
                ? parameterized.getActualTypeArguments()[0]
                : outputType;
        Type rawType = resultType instanceof ParameterizedType parameterized ? parameterized.getRawType() : resultType;
        // the result is built as a LinkedHashMap, so accept any type it can be assigned to (Map, Object, HashMap...)
        if (rawType instanceof Class<?> resultClass
                && resultClass != void.class
                && !resultClass.isAssignableFrom(LinkedHashMap.class)) {
            throw new IllegalArgumentException("DecisionRouterPlanner was created with activationThreshold="
                    + activationThreshold + ", so its result is a map from agent name to output, but the router agent "
                    + "returns " + resultType.getTypeName() + ": declare a Map return type, or remove the activation "
                    + "threshold to return the output of the most probable agent");
        }
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        // when resuming, the agents activated before the suspension or crash are restored instead of decided again
        if (activated == null) {
            DecisionResponse response = decide(planningContext.agenticScope());
            activated = activationThreshold == null
                    ? List.of(response.choice(QUESTION_NAME).value())
                    : routes.keySet().stream()
                            .filter(name -> response.yesNo(name).probability() >= activationThreshold)
                            .toList();
            LOG.info("Activating agents {} from the answers {}", activated, response.answers());
        }

        List<AgentInstance> pending = activated.stream()
                .filter(name -> !outputs.containsKey(name))
                .map(routes::get)
                .toList();
        return pending.isEmpty() ? done(result(planningContext.agenticScope())) : call(pending);
    }

    private DecisionResponse decide(AgenticScope agenticScope) {
        Map<String, Object> input = new LinkedHashMap<>();
        inputKeys.forEach(key -> input.put(key, agenticScope.readState(key)));

        return decisionModel.decide(
                DecisionRequest.builder().input(input).questions(questions).build());
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        AgentInvocation invocation = planningContext.previousAgentInvocation();
        String agentName = invocation.agentName();
        outputs.put(agentName, invocation.output());
        if (scopeOutput(agentName, planningContext.agenticScope()) instanceof DelayedResponse<?> delayedResponse
                && !delayedResponse.isDone()) {
            deferred.add(agentName);
        }
        // the loop resumes only when all the activated agents completed, so the last done action carries all outputs
        return done(result(planningContext.agenticScope()));
    }

    private Object result(AgenticScope agenticScope) {
        if (activationThreshold == null) {
            return outputOf(activated.get(0), agenticScope);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        activated.stream()
                .filter(outputs::containsKey)
                .forEach(agentName -> result.put(agentName, outputOf(agentName, agenticScope)));
        return result;
    }

    private Object outputOf(String agentName, AgenticScope agenticScope) {
        if (!deferred.contains(agentName)) {
            return outputs.get(agentName);
        }
        // a pending output is completed later in the scope, without blocking here if it is still pending
        Object scopeOutput = scopeOutput(agentName, agenticScope);
        return scopeOutput instanceof DelayedResponse<?> delayedResponse ? delayedResponse.result() : scopeOutput;
    }

    private Object scopeOutput(String agentName, AgenticScope agenticScope) {
        String outputKey = routes.get(agentName).outputKey();
        return outputKey == null ? null : agenticScope.state().get(outputKey);
    }

    @Override
    public Map<String, Object> executionState() {
        if (activated == null) {
            return Map.of();
        }
        return Map.of(
                ACTIVATED_STATE_KEY,
                activated,
                OUTPUTS_STATE_KEY,
                new HashMap<>(outputs),
                DEFERRED_STATE_KEY,
                List.copyOf(deferred));
    }

    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        if (state.get(ACTIVATED_STATE_KEY) instanceof List<?> activatedNames) {
            this.activated = activatedNames.stream().map(Object::toString).toList();
        }
        if (state.get(OUTPUTS_STATE_KEY) instanceof Map<?, ?> savedOutputs) {
            savedOutputs.forEach((name, output) -> outputs.put(name.toString(), output));
        }
        if (state.get(DEFERRED_STATE_KEY) instanceof List<?> deferredNames) {
            deferredNames.forEach(name -> deferred.add(name.toString()));
        }
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.ROUTER;
    }
}
