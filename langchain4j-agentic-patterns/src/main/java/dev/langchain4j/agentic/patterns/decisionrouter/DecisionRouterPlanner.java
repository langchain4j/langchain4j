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
 * Without an activation threshold, each subagent is an option of a single choice question, described by its
 * description, or by its name when it has none, and the planner invokes only the most probable subagent and returns
 * its output.
 * <p>
 * With an activation threshold, the planner asks instead one yes/no question per subagent, all in the same request
 * ("Should the agent '...' handle this request?"), invokes in parallel all the subagents whose probability of "yes"
 * is at least the threshold, and returns a map from the name of each invoked subagent to its output, so the method of
 * the router agent must return a {@code Map}, which is checked when the router is invoked. These probabilities are
 * independent of each other: several subagents can reach a high threshold, adding a subagent does not change the
 * probabilities of the others, and when no subagent reaches the threshold none is invoked and the result is an empty
 * map.
 * <p>
 * The planner identifies a subagent by its name, which names its option or its question. Several subagents can have
 * the same name: each of them is then identified by its {@link AgentInstance#agentId() id} instead, the decision
 * model tells them apart by their descriptions, and in the map returned with an activation threshold the output of
 * each of them is under its id.
 * <p>
 * Only the subagents whose activation condition holds are offered to the decision model. When none does, the model
 * isn't asked at all and the result is {@code null}, or an empty map with an activation threshold. Without an
 * activation threshold, when only one does, it is invoked without asking the model.
 * <p>
 * A planner instance holds the state of a single invocation and is not thread-safe, so the agentic system must be
 * given a supplier creating a new instance at each invocation, for example
 * {@code () -> new DecisionRouterPlanner(decisionModel)}, while the decision model can be shared. An instance that is
 * initialized a second time fails with an {@link IllegalStateException}.
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

    // the names that several subagents have: these subagents are identified by their ids, the others by their names
    private final Set<String> sharedNames = new HashSet<>();

    // persisted to resume after a suspension or a crash: the keys of the activated agents, the outputs of those
    // already invoked, and the keys of those whose output was still pending, like an agent waiting for a human
    private List<String> activated;
    private final Map<String, Object> outputs = new HashMap<>();
    private final Set<String> deferred = new HashSet<>();

    /**
     * Creates a router invoking only the subagent that the decision model considers the most probable.
     * <p>
     * When the router is invoked, before asking the decision model, it fails with an
     * {@link IllegalArgumentException} if the router agent is not a typed agent interface with at least one argument
     * to send to the decision model.
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
     * The result of the router is then a map from the name of each invoked subagent to its output, or from its id
     * when several subagents have that name, so the method of the router agent must return a {@code Map} (or a
     * {@code ResultWithAgenticScope} of a {@code Map}). When the router is invoked, before asking the decision model,
     * it fails with an {@link IllegalArgumentException} if it does not, or if the router agent is not a typed agent
     * interface with at least one argument to send to the decision model.
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
        if (inputKeys != null) {
            throw new IllegalStateException("A DecisionRouterPlanner instance holds the state of a single invocation "
                    + "and cannot be reused: give the agentic system a supplier creating a new instance at each "
                    + "invocation, for example planner(() -> new DecisionRouterPlanner(decisionModel))");
        }
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

        Set<String> names = new HashSet<>();
        for (AgentInstance agent : initPlanningContext.subagents()) {
            if (!names.add(agent.name())) {
                sharedNames.add(agent.name());
            }
        }
        initPlanningContext.subagents().forEach(agent -> routes.put(key(agent), agent));
    }

    /** The key identifying a subagent: its name, or its id when several subagents have that name. */
    private String key(AgentInstance agent) {
        return key(agent.name(), agent.agentId());
    }

    private String key(String agentName, String agentId) {
        return sharedNames.contains(agentName) ? agentId : agentName;
    }

    /**
     * A single choice question among the candidates without an activation threshold, one yes/no question per
     * candidate (named after its key) with an activation threshold.
     */
    private Map<String, Question> questions(List<AgentInstance> candidates) {
        if (activationThreshold != null) {
            Map<String, Question> questions = new LinkedHashMap<>();
            candidates.forEach(agent -> questions.put(key(agent), relevanceQuestion(agent)));
            return questions;
        }
        ChoiceQuestion.Builder choice = ChoiceQuestion.builder().text(QUESTION_TEXT);
        // an agent without a description is described by its name
        candidates.forEach(agent ->
                choice.option(key(agent), isNullOrBlank(agent.description()) ? agent.name() : agent.description()));
        return Map.of(QUESTION_NAME, choice.build());
    }

    private static YesNoQuestion relevanceQuestion(AgentInstance agent) {
        String description = isNullOrBlank(agent.description()) ? "" : " (" + agent.description() + ")";
        return YesNoQuestion.from("Should the agent '" + agent.name() + "'" + description + " handle this request?");
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
            // only the agents that would run now are offered to the decision model
            List<AgentInstance> candidates =
                    routes.values().stream().filter(planningContext::isActive).toList();
            activated = candidates.isEmpty() ? List.of() : decide(planningContext.agenticScope(), candidates);
        } else {
            activated.stream()
                    .filter(key -> !routes.containsKey(key))
                    .findFirst()
                    .ifPresent(key -> {
                        throw new IllegalStateException("Cannot resume DecisionRouterPlanner with the subagent '"
                                + key + "' saved in its execution state, since the subagents changed after it "
                                + "was saved");
                    });
        }

        List<AgentInstance> pending = activated.stream()
                .filter(key -> !outputs.containsKey(key))
                .map(routes::get)
                .toList();
        return pending.isEmpty() ? done(result(planningContext.agenticScope())) : call(pending);
    }

    private List<String> decide(AgenticScope agenticScope, List<AgentInstance> candidates) {
        if (activationThreshold == null && candidates.size() == 1) {
            return List.of(key(candidates.get(0)));
        }

        Map<String, Object> input = new LinkedHashMap<>();
        inputKeys.forEach(key -> input.put(key, agenticScope.readState(key)));

        DecisionResponse response = decisionModel.decide(DecisionRequest.builder()
                .inputFields(input)
                .questions(questions(candidates))
                .build());
        List<String> chosen = activationThreshold == null
                ? List.of(response.choice(QUESTION_NAME).value())
                : candidates.stream()
                        .map(this::key)
                        .filter(key -> response.yesNo(key).probability() >= activationThreshold)
                        .toList();
        LOG.info("Activating agents {} from the answers {}", chosen, response.answers());
        return chosen;
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        AgentInvocation invocation = planningContext.previousAgentInvocation();
        String key = key(invocation.agentName(), invocation.agentId());
        // a skipped agent produced no output, so it is left out of the result
        if (!invocation.skipped()) {
            outputs.put(key, invocation.output());
            if (scopeOutput(key, planningContext.agenticScope()) instanceof DelayedResponse<?> delayedResponse
                    && !delayedResponse.isDone()) {
                deferred.add(key);
            }
        }
        // the loop resumes only when all the activated agents completed, so the last done action carries all outputs
        return done(result(planningContext.agenticScope()));
    }

    private Object result(AgenticScope agenticScope) {
        if (activationThreshold == null) {
            return activated.isEmpty() ? null : outputOf(activated.get(0), agenticScope);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        activated.stream()
                .filter(outputs::containsKey)
                .forEach(key -> result.put(key, outputOf(key, agenticScope)));
        return result;
    }

    private Object outputOf(String key, AgenticScope agenticScope) {
        if (!deferred.contains(key)) {
            return outputs.get(key);
        }
        // a pending output is completed later in the scope, without blocking here if it is still pending
        Object scopeOutput = scopeOutput(key, agenticScope);
        return scopeOutput instanceof DelayedResponse<?> delayedResponse ? delayedResponse.result() : scopeOutput;
    }

    private Object scopeOutput(String key, AgenticScope agenticScope) {
        String outputKey = routes.get(key).outputKey();
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
        if (state.get(ACTIVATED_STATE_KEY) instanceof List<?> activatedKeys) {
            this.activated = activatedKeys.stream().map(Object::toString).toList();
        }
        if (state.get(OUTPUTS_STATE_KEY) instanceof Map<?, ?> savedOutputs) {
            savedOutputs.forEach((key, output) -> outputs.put(key.toString(), output));
        }
        if (state.get(DEFERRED_STATE_KEY) instanceof List<?> deferredKeys) {
            deferredKeys.forEach(key -> deferred.add(key.toString()));
        }
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.ROUTER;
    }
}
