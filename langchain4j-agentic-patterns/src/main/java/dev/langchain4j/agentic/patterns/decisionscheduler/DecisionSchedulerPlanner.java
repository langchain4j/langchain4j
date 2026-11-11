package dev.langchain4j.agentic.patterns.decisionscheduler;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agentic.agent.AgentUnavailableException;
import dev.langchain4j.agentic.internal.DelayedResponse;
import dev.langchain4j.agentic.internal.SuspendedResponse;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.input.PromptTemplate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A planner that chooses, one step at a time, which of its subagents to invoke next, by asking a
 * {@link DecisionModel} how useful each of them would be now, and that stops when the decision model considers a
 * goal reached. Compared with a supervisor, each step costs one decision call instead of a call to a chat model, but
 * a decision model cannot generate the arguments of the next agent: every subagent must read its inputs from the
 * {@link AgenticScope}.
 * <p>
 * At each step the candidates are the subagents that can run now: all their mandatory arguments are in the scope,
 * their activation condition holds, and they either never ran or had one of their arguments changed since their last
 * run. An argument is mandatory when it has no default value and is not optional. The planner then sends a single
 * request to the decision model with:
 * <ul>
 *   <li>one scale question per candidate, by default "How useful would it be to run the agent '...' (its
 *   description) now, to reach this goal: ...?", with the levels "USELESS", "MARGINAL", "USEFUL" and "ESSENTIAL".
 *   The score of the candidate is the mean level of the answer, scaled between 0 (the lowest level) and 1 (the
 *   highest level);</li>
 *   <li>one yes/no question, by default "Has this goal been reached: ...?".</li>
 * </ul>
 * The texts of both questions and the levels can be replaced through the builder.
 * The planner identifies a subagent by its name, which is also the name of its question. Several subagents can have
 * the same name: each of them is then identified by its {@link AgentInstance#agentId() id} instead, and the decision
 * model tells them apart by their descriptions. A subagent without a description is described by its name.
 * <p>
 * If the probability that the goal has been reached is at least the goal threshold the planner stops. Otherwise it
 * invokes the candidate with the highest score, the first declared one when several have the same score, provided
 * that this score is at least the activation threshold. The planner also stops when no subagent can run, when no
 * candidate reaches the activation threshold, and after the maximum number of invocations, where the decision model
 * is not asked again and a warning is logged. In all cases the result is the value found in the scope under the
 * output key of the planner agent. When one of the subagents writes that output key, the goal question is not asked
 * until the key is in the scope, since the goal cannot be reached before the result exists, and stopping in one of
 * these three ways before then fails with an {@link IllegalStateException} saying why the planner stopped, so that
 * giving up cannot be mistaken for an empty result: a caller that wants a fallback has to catch it.
 * <p>
 * The input of the decision is made of the arguments of the planner agent itself, read from the scope, plus the
 * outputs of the subagents that are in the scope, each under its output key. The arguments are sent as they are, so
 * they can be strings, numbers, booleans, maps and lists of them, or {@link Content}s, and an argument of any other
 * type is rejected by the decision model with an error explaining which types are supported: the application decides
 * which of its objects are sent to the provider of the decision model. The outputs of the subagents are sent as they
 * are when they are such values, and converted to maps and lists through a JSON round trip otherwise, which
 * serializes all their fields, private ones included. This state is sent at every step to the provider of the
 * decision model, which can differ from the provider of the chat models of the subagents: the outputs that must not
 * reach it can be left out of the input through {@link Builder#excludedOutputKeys(String...)}. The decision model
 * sees this state only: it is not told which subagents already ran, and the work of a subagent without an output
 * key, or whose output key is left out, is invisible to it.
 * <p>
 * A subagent whose description cannot be read because it is unavailable, like an A2A agent whose server is down, is
 * left out of the candidates for the rest of the invocation, with a warning. A failure of the decision model fails
 * the invocation.
 * <p>
 * The planner saves as its execution state the number of invocations, the subagents that cannot run again yet and
 * the subagent being invoked, so that after a suspension or a crash it resumes from that subagent. This state refers
 * to the subagents by their names, so it does not depend on the order in which they are declared. Only the subagents
 * sharing a name are referred to by their ids, which contain their position, and have to be declared in the same
 * position when the planner is resumed. When a subagent waits for a human the planner suspends without asking the
 * decision model, and decides on the answer when it is resumed. The state is saved only after each decision, so a
 * crash during a decision call makes the subagent that had just completed run again, and a crash before the first
 * subagent completes makes the planner decide again. A subagent whose output can be delayed, like a
 * human-in-the-loop or an asynchronous agent, must declare an output key, since the planner reads its answer from
 * the scope: the decision following an asynchronous subagent waits for its answer, unless its output key is left
 * out of the input.
 * <p>
 * Each step is a greedy choice with no lookahead, so this planner fits the tasks where the most useful next step can
 * be told from the current state, not those that need a plan of several steps. The scores and the probabilities are
 * not calibrated in the same way by every decision model, so both thresholds must be tuned on the decision model in
 * use and tuned again when it changes. The state and the descriptions of the subagents, like those of remote A2A
 * agents, are untrusted input to the decision, so a crafted request, agent output or description can influence it:
 * this planner is not a security boundary, and a subagent with side effects must be guarded on its own, for instance
 * through an activation condition.
 * <p>
 * A planner instance holds the state of a single invocation and is not thread-safe, so the agentic system must be
 * given a supplier creating a new instance at each invocation, for example
 * {@code () -> DecisionSchedulerPlanner.builder().decisionModel(decisionModel).goal("...").goalThreshold(0.6)
 * .activationThreshold(0.3).build()}, while the decision model can be shared. An instance that is initialized a
 * second time fails with an {@link IllegalStateException}.
 */
@Experimental
public class DecisionSchedulerPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(DecisionSchedulerPlanner.class);

    /**
     * The default template of the yes/no question asking whether the goal has been reached, with the variable
     * {@code {{goal}}}.
     */
    public static final PromptTemplate DEFAULT_GOAL_QUESTION_TEMPLATE =
            PromptTemplate.from("Has this goal been reached: {{goal}}?");

    /**
     * The default template of the scale question asking how useful it would be to invoke a subagent now, with the
     * variables {@code {{name}}} and {@code {{description}}} of the subagent, and {@code {{goal}}}.
     */
    public static final PromptTemplate DEFAULT_USEFULNESS_QUESTION_TEMPLATE = PromptTemplate.from(
            "How useful would it be to run the agent '{{name}}' ({{description}}) now, to reach this goal: {{goal}}?");

    /** The default levels of the usefulness question, from the lowest to the highest. */
    public static final List<String> DEFAULT_USEFULNESS_LEVELS = List.of("USELESS", "MARGINAL", "USEFUL", "ESSENTIAL");

    /** The default maximum number of subagent invocations. */
    public static final int DEFAULT_MAX_INVOCATIONS = 10;

    static final String GOAL_QUESTION_NAME = "goal-reached";

    private static final String DECISION_SCHEDULER_KEY_PREFIX = "decision-scheduler-";
    private static final String INVOCATIONS_STATE_KEY = DECISION_SCHEDULER_KEY_PREFIX + "invocations";
    private static final String SPENT_STATE_KEY = DECISION_SCHEDULER_KEY_PREFIX + "spent";
    private static final String CURRENT_STATE_KEY = DECISION_SCHEDULER_KEY_PREFIX + "current";
    private static final String PENDING_STATE_KEY = DECISION_SCHEDULER_KEY_PREFIX + "pending";

    private final DecisionModel decisionModel;
    private final String goal;
    private final YesNoQuestion goalQuestion;
    private final PromptTemplate usefulnessQuestionTemplate;
    private final List<String> usefulnessLevels;
    private final double goalThreshold;
    private final double activationThreshold;
    private final int maxInvocations;
    private final Set<String> excludedOutputKeys;

    private List<String> inputKeys;
    private final Map<String, Subagent> subagents = new LinkedHashMap<>();

    // the output key of the planner agent when a subagent writes it: the goal isn't asked before it is in the scope
    private String resultKey;

    // persisted to resume after a suspension or a crash: the number of subagents invoked so far, the keys of those
    // that ran and had no argument changed since, the key of the one being invoked (null between two steps), and
    // whether its answer was still pending, like an answer waited from a human
    private int invocations;
    private final Set<String> spent = new LinkedHashSet<>();
    private String current;
    private boolean pending;

    // the subagents left out of this invocation because their description cannot be read
    private final Set<String> unavailable = new HashSet<>();

    // the names that several subagents have, or that a subagent has in common with the goal question: these
    // subagents are identified by their ids, the others by their names
    private final Set<String> sharedNames = new HashSet<>();

    /** A subagent with the key identifying it, which is also the name of its question. */
    private record Subagent(String key, AgentInstance agent, List<String> mandatoryArguments, Set<String> arguments) {

        static Subagent of(String key, AgentInstance agent) {
            // framework-provided arguments, like @MemoryId, are not read from the state
            List<AgentArgument> arguments = agent.arguments().stream()
                    .filter(argument -> !argument.name().startsWith("@"))
                    .toList();
            return new Subagent(
                    key,
                    agent,
                    arguments.stream()
                            .filter(argument -> argument.defaultValue() == null && !argument.isOptional())
                            .map(AgentArgument::name)
                            .toList(),
                    arguments.stream().map(AgentArgument::name).collect(Collectors.toSet()));
        }
    }

    private DecisionSchedulerPlanner(Builder builder) {
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        this.goal = ensureNotBlank(builder.goal, "goal");
        this.goalQuestion =
                goalQuestion(getOrDefault(builder.goalQuestionTemplate, DEFAULT_GOAL_QUESTION_TEMPLATE), goal);
        this.usefulnessQuestionTemplate = validUsefulnessQuestionTemplate(
                getOrDefault(builder.usefulnessQuestionTemplate, DEFAULT_USEFULNESS_QUESTION_TEMPLATE));
        this.usefulnessLevels = List.copyOf(getOrDefault(builder.usefulnessLevels, DEFAULT_USEFULNESS_LEVELS));
        if (usefulnessLevels.size() < 2) {
            throw illegalArgument("The usefulness question needs at least 2 levels, but has: %s", usefulnessLevels);
        }
        this.goalThreshold = validThreshold(builder.goalThreshold, "Goal threshold");
        this.activationThreshold = validThreshold(builder.activationThreshold, "Activation threshold");
        this.maxInvocations = getOrDefault(builder.maxInvocations, DEFAULT_MAX_INVOCATIONS);
        if (maxInvocations < 1) {
            throw illegalArgument("Max invocations must be at least 1, but is: %s", maxInvocations);
        }
        this.excludedOutputKeys = Set.copyOf(getOrDefault(builder.excludedOutputKeys, List.of()));
    }

    private static YesNoQuestion goalQuestion(PromptTemplate template, String goal) {
        String text;
        try {
            text = template.apply(Map.of("goal", goal)).text();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "The goal question template can only use the {{goal}} variable, but was: " + template.template(),
                    e);
        }
        return YesNoQuestion.from(text);
    }

    private static PromptTemplate validUsefulnessQuestionTemplate(PromptTemplate template) {
        if (!template.template().contains("{{name}}") && !template.template().contains("{{description}}")) {
            throw new IllegalArgumentException("The usefulness question template must contain {{name}} or "
                    + "{{description}}, to tell the subagents apart, but was: " + template.template());
        }
        try {
            template.apply(Map.of("name", "name", "description", "description", "goal", "goal"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "The usefulness question template can only use the {{name}}, {{description}} and {{goal}} "
                            + "variables, but was: " + template.template(),
                    e);
        }
        return template;
    }

    private static double validThreshold(Double threshold, String name) {
        if (threshold == null) {
            throw illegalArgument("%s must be set", name);
        }
        if (!(threshold > 0.0 && threshold < 1.0)) {
            throw illegalArgument("%s must be greater than 0.0 and less than 1.0, but is: %s", name, threshold);
        }
        return threshold;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        if (inputKeys != null) {
            throw new IllegalStateException("A DecisionSchedulerPlanner instance holds the state of a single "
                    + "invocation and cannot be reused: give the agentic system a supplier creating a new instance "
                    + "at each invocation, for example planner(() -> DecisionSchedulerPlanner.builder()...build())");
        }
        AgentInstance plannerAgent = initPlanningContext.plannerAgent();
        // framework-provided arguments, like @MemoryId, are not part of the state
        this.inputKeys = plannerAgent.arguments().stream()
                .map(AgentArgument::name)
                .filter(name -> !name.startsWith("@"))
                .toList();
        if (inputKeys.isEmpty()) {
            throw new IllegalArgumentException("DecisionSchedulerPlanner requires a typed agent interface whose "
                    + "method arguments are sent to the decision model together with the outputs of the subagents");
        }

        // the name of the goal question is taken
        Set<String> names = new HashSet<>(Set.of(GOAL_QUESTION_NAME));
        for (AgentInstance agent : initPlanningContext.subagents()) {
            if (!names.add(agent.name())) {
                sharedNames.add(agent.name());
            }
        }
        initPlanningContext.subagents().forEach(agent -> {
            String key = key(agent.name(), agent.agentId());
            subagents.put(key, Subagent.of(key, agent));
        });

        // a key that no subagent writes is likely misspelled, and the output meant to be left out would be sent
        Set<String> outputKeys = subagents.values().stream()
                .map(subagent -> subagent.agent().outputKey())
                .filter(key -> !isNullOrBlank(key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        excludedOutputKeys.stream()
                .filter(key -> !outputKeys.contains(key))
                .findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException("DecisionSchedulerPlanner cannot leave '" + key + "' out of "
                            + "the input of the decision model, since it is not the output key of one of its "
                            + "subagents, which are: " + outputKeys);
                });

        String outputKey = plannerAgent.outputKey();
        boolean writtenByASubagent = !isNullOrBlank(outputKey)
                && subagents.values().stream()
                        .anyMatch(subagent -> outputKey.equals(subagent.agent().outputKey()));
        this.resultKey = writtenByASubagent ? outputKey : null;
        if (resultKey != null && excludedOutputKeys.contains(resultKey)) {
            throw new IllegalArgumentException("DecisionSchedulerPlanner cannot leave '" + resultKey + "' out of the "
                    + "input of the decision model, since it is the output key of the planner agent: the decision "
                    + "model judges on this result whether the goal has been reached");
        }
    }

    /** The key identifying a subagent: its name, or its id when that name is shared. */
    private String key(String agentName, String agentId) {
        return sharedNames.contains(agentName) ? agentId : agentName;
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        if (current == null) {
            return decide(planningContext);
        }
        // resuming after a suspension or a crash
        Subagent subagent = subagents.get(current);
        if (subagent == null) {
            throw new IllegalStateException("Cannot resume DecisionSchedulerPlanner at the subagent '" + current
                    + "', since the subagents changed after its execution state was saved");
        }
        if (!pending) {
            // it was being invoked: it runs again, without asking the decision model
            return call(subagent.agent());
        }
        if (scopeOutput(subagent, planningContext.agenticScope()) instanceof DelayedResponse<?> delayedResponse
                && !delayedResponse.isDone()) {
            // resumed before the answer arrived
            return suspend();
        }
        pending = false;
        completed(subagent);
        return decide(planningContext);
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        AgentInvocation invocation = planningContext.previousAgentInvocation();
        Subagent subagent = subagents.get(key(invocation.agentName(), invocation.agentId()));
        if (invocation.skipped()) {
            // a skipped agent didn't change the state, so it doesn't make other agents able to run again
            LOG.info("Subagent '{}' skipped ({})", subagent.key(), invocation.skipReason());
            current = null;
            return decide(planningContext);
        }

        if (scopeOutput(subagent, planningContext.agenticScope()) instanceof DelayedResponse<?> delayedResponse) {
            if (!delayedResponse.isDone() && delayedResponse instanceof SuspendedResponse<?>) {
                // the next step is decided on the answer, for instance from a human, when the system is resumed
                pending = true;
                return suspend();
            }
        } else if (DelayedResponse.PENDING.equals(invocation.output())) {
            throw new IllegalStateException("The answer of the subagent '" + subagent.key() + "' is not available "
                    + "yet, but the subagent has no output key where DecisionSchedulerPlanner can read it when it "
                    + "arrives: declare an output key on every subagent whose answer can be delayed");
        }
        completed(subagent);
        return decide(planningContext);
    }

    /** The subagents reading the output of the one that just ran can run again. */
    private void completed(Subagent subagent) {
        current = null;
        String outputKey = subagent.agent().outputKey();
        if (!isNullOrBlank(outputKey)) {
            subagents.values().stream()
                    .filter(other -> other.arguments().contains(outputKey))
                    .forEach(other -> spent.remove(other.key()));
        }
    }

    private Action decide(PlanningContext planningContext) {
        AgenticScope agenticScope = planningContext.agenticScope();
        if (invocations >= maxInvocations) {
            ensureResult(agenticScope, "the maximum number of invocations was reached");
            LOG.warn(
                    "Stopping after the maximum of {} invocations, without asking whether the goal has been reached",
                    invocations);
            return done();
        }
        List<Subagent> candidates = subagents.values().stream()
                .filter(subagent -> isCandidate(subagent, planningContext))
                .toList();
        if (candidates.isEmpty()) {
            ensureResult(agenticScope, "no subagent can run");
            LOG.info("Stopping after {} invocations, since no subagent can run", invocations);
            return done();
        }

        boolean askGoal = resultKey == null || agenticScope.hasState(resultKey);
        DecisionRequest.Builder request = DecisionRequest.builder().inputFields(state(agenticScope));
        if (askGoal) {
            request.question(GOAL_QUESTION_NAME, goalQuestion);
        }
        candidates.forEach(candidate -> request.question(candidate.key(), usefulnessQuestion(candidate.agent())));
        DecisionResponse response = decisionModel.decide(request.build());

        Double goalProbability =
                askGoal ? response.yesNo(GOAL_QUESTION_NAME).probability() : null;
        if (goalProbability != null && goalProbability >= goalThreshold) {
            LOG.info("Goal reached after {} invocations, with probability {}", invocations, goalProbability);
            return done();
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        candidates.forEach(candidate ->
                scores.put(candidate.key(), response.scale(candidate.key()).mean() / (usefulnessLevels.size() - 1)));
        // the first declared candidate wins a tie
        Subagent best = candidates.get(0);
        for (Subagent candidate : candidates) {
            if (scores.get(candidate.key()) > scores.get(best.key())) {
                best = candidate;
            }
        }
        if (scores.get(best.key()) < activationThreshold) {
            ensureResult(
                    agenticScope,
                    "no subagent is useful enough: the scores " + scores + " are below the activation threshold "
                            + activationThreshold);
            LOG.info(
                    "Stopping after {} invocations, since no subagent is useful enough: scores {}, goal probability {}",
                    invocations,
                    scores,
                    goalProbability);
            return done();
        }

        spent.add(best.key());
        current = best.key();
        invocations++;
        LOG.info(
                "Invoking subagent '{}' (invocation #{}): scores {}, goal probability {}",
                best.key(),
                invocations,
                scores,
                goalProbability);
        return call(best.agent());
    }

    /**
     * Fails when the planner is about to stop while the result that one of its subagents writes is not in the scope,
     * so that giving up is not mistaken for an empty result.
     */
    private void ensureResult(AgenticScope agenticScope, String reason) {
        if (resultKey != null && !agenticScope.hasState(resultKey)) {
            throw new IllegalStateException("DecisionSchedulerPlanner stopped after " + invocations
                    + " invocation(s) before any subagent wrote the result under the output key '" + resultKey
                    + "', since " + reason);
        }
    }

    private boolean isCandidate(Subagent subagent, PlanningContext planningContext) {
        return !spent.contains(subagent.key())
                && subagent.mandatoryArguments().stream().allMatch(planningContext.agenticScope()::hasState)
                && planningContext.isActive(subagent.agent())
                && isAvailable(subagent);
    }

    /**
     * A subagent that is unavailable, like an A2A agent whose description cannot be read because its server is down,
     * is left out of the candidates for the rest of this invocation, with a warning. Only an
     * {@link AgentUnavailableException} means that: any other failure of the description propagates.
     */
    private boolean isAvailable(Subagent subagent) {
        if (unavailable.contains(subagent.key())) {
            return false;
        }
        try {
            subagent.agent().description();
            return true;
        } catch (AgentUnavailableException e) {
            unavailable.add(subagent.key());
            LOG.warn(
                    "The subagent '{}' is left out of this invocation, because it is unavailable and its "
                            + "description cannot be read",
                    subagent.key(),
                    e);
            return false;
        }
    }

    private ScaleQuestion usefulnessQuestion(AgentInstance agent) {
        String description = agent.description();
        String text = usefulnessQuestionTemplate
                .apply(Map.of(
                        "name",
                        agent.name(),
                        "description",
                        isNullOrBlank(description) ? agent.name() : description,
                        "goal",
                        goal))
                .text();
        return ScaleQuestion.from(text, usefulnessLevels);
    }

    /**
     * The state sent to the decision model: the arguments of the planner agent as they are, and the outputs of the
     * subagents that are in the scope, converted to values that the decision model accepts. An excluded output key
     * is left out, also when it is an argument of the planner agent too.
     */
    private Map<String, Object> state(AgenticScope agenticScope) {
        Map<String, Object> state = new LinkedHashMap<>();
        inputKeys.stream()
                .filter(key -> !excludedOutputKeys.contains(key))
                .forEach(key -> state.put(key, agenticScope.readState(key)));
        for (Subagent subagent : subagents.values()) {
            String outputKey = subagent.agent().outputKey();
            if (!isNullOrBlank(outputKey)
                    && !excludedOutputKeys.contains(outputKey)
                    && !state.containsKey(outputKey)
                    && agenticScope.hasState(outputKey)) {
                // reading the output of an asynchronous subagent waits for it
                state.put(outputKey, plainValue(agenticScope.readState(outputKey)));
            }
        }
        return state;
    }

    /**
     * The value as the decision model accepts it: a plain value is sent as it is, any other object is converted to
     * maps and lists through a JSON round trip.
     */
    private static Object plainValue(Object value) {
        return isPlain(value) ? value : Json.fromJson(Json.toJson(value), Object.class);
    }

    private static boolean isPlain(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Content) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().allMatch(DecisionSchedulerPlanner::isPlain);
        }
        return value instanceof Collection<?> collection
                && collection.stream().allMatch(DecisionSchedulerPlanner::isPlain);
    }

    private static Object scopeOutput(Subagent subagent, AgenticScope agenticScope) {
        String outputKey = subagent.agent().outputKey();
        return isNullOrBlank(outputKey) ? null : agenticScope.state().get(outputKey);
    }

    @Override
    public Map<String, Object> executionState() {
        if (invocations == 0) {
            return Map.of();
        }
        Map<String, Object> state = new HashMap<>();
        state.put(INVOCATIONS_STATE_KEY, invocations);
        state.put(SPENT_STATE_KEY, List.copyOf(spent));
        state.put(PENDING_STATE_KEY, pending);
        if (current != null) {
            state.put(CURRENT_STATE_KEY, current);
        }
        return state;
    }

    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        if (state.get(INVOCATIONS_STATE_KEY) instanceof Number savedInvocations) {
            this.invocations = savedInvocations.intValue();
        }
        if (state.get(SPENT_STATE_KEY) instanceof List<?> savedSpent) {
            savedSpent.forEach(key -> spent.add(key.toString()));
        }
        if (state.get(CURRENT_STATE_KEY) instanceof String savedCurrent) {
            this.current = savedCurrent;
        }
        if (state.get(PENDING_STATE_KEY) instanceof Boolean savedPending) {
            this.pending = savedPending;
        }
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.STAR;
    }

    public static final class Builder {

        private DecisionModel decisionModel;
        private String goal;
        private Double goalThreshold;
        private Double activationThreshold;
        private Integer maxInvocations;
        private PromptTemplate goalQuestionTemplate;
        private PromptTemplate usefulnessQuestionTemplate;
        private List<String> usefulnessLevels;
        private List<String> excludedOutputKeys;

        private Builder() {}

        /**
         * Sets the decision model scoring the subagents and judging whether the goal has been reached. Mandatory.
         */
        public Builder decisionModel(DecisionModel decisionModel) {
            this.decisionModel = decisionModel;
            return this;
        }

        /**
         * Sets the description of the goal, for instance "The reply answers every question of the customer's
         * ticket". It is sent to the decision model in every question. Mandatory, and not blank.
         */
        public Builder goal(String goal) {
            this.goal = goal;
            return this;
        }

        /**
         * Sets the minimum probability that the goal has been reached for the planner to stop. Mandatory, strictly
         * between 0 and 1.
         */
        public Builder goalThreshold(Double goalThreshold) {
            this.goalThreshold = goalThreshold;
            return this;
        }

        /**
         * Sets the minimum score for the most useful subagent to be invoked: when no subagent reaches it the planner
         * stops. The score goes from 0 (the lowest usefulness level) to 1 (the highest one): with the default levels,
         * "USELESS" is 0, "MARGINAL" 1/3, "USEFUL" 2/3 and "ESSENTIAL" 1. Mandatory, strictly between 0 and 1.
         */
        public Builder activationThreshold(Double activationThreshold) {
            this.activationThreshold = activationThreshold;
            return this;
        }

        /**
         * Sets the maximum number of subagent invocations, after which the planner stops with a warning, without
         * asking the decision model again, or fails if the result has not been written yet. At least 1.
         * <p>
         * Default value is {@value DecisionSchedulerPlanner#DEFAULT_MAX_INVOCATIONS}.
         */
        public Builder maxInvocations(Integer maxInvocations) {
            this.maxInvocations = maxInvocations;
            return this;
        }

        /**
         * Sets the template of the yes/no question asking whether the goal has been reached, which can use the
         * {@code {{goal}}} variable (the goal set with {@link #goal(String)}).
         * <p>
         * Default value is {@link DecisionSchedulerPlanner#DEFAULT_GOAL_QUESTION_TEMPLATE}.
         */
        public Builder goalQuestionTemplate(PromptTemplate goalQuestionTemplate) {
            this.goalQuestionTemplate = goalQuestionTemplate;
            return this;
        }

        /**
         * Sets the template of the scale question asking how useful it would be to invoke a subagent now, which must
         * contain the {@code {{name}}} or the {@code {{description}}} variable (the name and the description of the
         * subagent, or its name when it has no description), and can use the {@code {{goal}}} variable.
         * <p>
         * Default value is {@link DecisionSchedulerPlanner#DEFAULT_USEFULNESS_QUESTION_TEMPLATE}.
         */
        public Builder usefulnessQuestionTemplate(PromptTemplate usefulnessQuestionTemplate) {
            this.usefulnessQuestionTemplate = usefulnessQuestionTemplate;
            return this;
        }

        /**
         * Sets the levels of the usefulness question, at least 2, from the lowest to the highest. The score of a
         * subagent is the mean level of the answer scaled between 0 (the lowest level) and 1 (the highest one).
         * <p>
         * Default value is {@link DecisionSchedulerPlanner#DEFAULT_USEFULNESS_LEVELS}.
         */
        public Builder usefulnessLevels(List<String> usefulnessLevels) {
            this.usefulnessLevels = usefulnessLevels;
            return this;
        }

        /**
         * Sets the output keys of the subagents to leave out of the input of the decision model, for instance because
         * they hold data that must not be sent to its provider. The subagents still read these outputs from the
         * scope, but the decision model scores the subagents and judges the goal without seeing them, so only the
         * outputs that these decisions do not need should be left out. A key left out is not sent even when it is
         * also an argument of the planner agent. The output key of the planner agent cannot be left out, since the
         * goal is judged on the result.
         * <p>
         * By default no output key is left out.
         */
        public Builder excludedOutputKeys(String... excludedOutputKeys) {
            this.excludedOutputKeys = excludedOutputKeys == null ? null : List.of(excludedOutputKeys);
            return this;
        }

        /**
         * When the planner is initialized, at each invocation, it fails with an {@link IllegalArgumentException} if
         * the planner agent is not a typed agent interface with at least one argument to send to the decision model,
         * or if one of the output keys to leave out is not the output key of one of its subagents, or is the
         * output key of the planner agent.
         *
         * @throws IllegalArgumentException if the decision model is {@code null}, if the goal is {@code null} or
         *         blank, if a threshold is not set or is not strictly between 0 and 1, if the maximum number of
         *         invocations is less than 1, if a question template uses other variables than its own or the
         *         usefulness question template contains neither {@code {{name}}} nor {@code {{description}}}, or if
         *         there are fewer than 2 usefulness levels
         */
        public DecisionSchedulerPlanner build() {
            return new DecisionSchedulerPlanner(this);
        }
    }
}
