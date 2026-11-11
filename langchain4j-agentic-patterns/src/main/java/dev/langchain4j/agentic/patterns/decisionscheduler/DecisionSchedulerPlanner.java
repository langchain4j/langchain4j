package dev.langchain4j.agentic.patterns.decisionscheduler;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
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
import java.util.Arrays;
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
 *   <li>one scale question per candidate, "How useful would it be to run the agent '...' (its description) now, to
 *   reach this goal: ...?", with the levels "USELESS", "MARGINAL", "USEFUL" and "ESSENTIAL". The score of the
 *   candidate is the mean level of the answer, scaled between 0 and 1;</li>
 *   <li>one yes/no question, "Has this goal been reached: ...?".</li>
 * </ul>
 * The planner identifies a subagent by its {@link AgentInstance#agentId() id}, which is also the name of its
 * question, so several subagents can have the same name: the decision model then tells them apart by their
 * descriptions.
 * <p>
 * If the probability that the goal has been reached is at least the goal threshold the planner stops. Otherwise it
 * invokes the candidate with the highest score, the first declared one when several have the same score, provided
 * that this score is at least the activation threshold. The planner also stops, without failing, when no subagent
 * can run, when no candidate reaches the activation threshold, and after the maximum number of invocations, where
 * only the goal question is asked and a warning is logged if the goal is not reached. In all cases the result is the
 * value found in the scope under the output key of the planner agent, which is {@code null} if no subagent wrote it.
 * When one of the subagents writes that output key, the goal question is not asked until the key is in the scope,
 * since the goal cannot be reached before the result exists.
 * <p>
 * The input of the decision is made of the arguments of the planner agent itself, read from the scope, plus the
 * outputs of the subagents that are in the scope, each under its output key. The arguments are sent as they are, so
 * they can be strings, numbers, booleans, maps and lists of them, or {@link Content}s, and an argument of any other
 * type is rejected by the decision model with an error explaining which types are supported: the application decides
 * which of its objects are sent to the provider of the decision model. The outputs of the subagents are sent as they
 * are when they are such values, and converted to maps and lists through a JSON round trip otherwise. The decision
 * model sees this state only: it is not told which subagents already ran, and the work of a subagent without an
 * output key is invisible to it.
 * <p>
 * A subagent whose description cannot be read because it is unavailable, like an A2A agent whose server is down, is
 * left out of the candidates for the rest of the invocation, with a warning. A failure of the decision model fails
 * the invocation.
 * <p>
 * The planner saves as its execution state the number of invocations, the subagents that cannot run again yet and
 * the subagent being invoked, so that after a suspension or a crash it resumes from that subagent. This state refers
 * to the subagents by their ids, which contain their position, so the planner has to be resumed with the same
 * subagents declared in the same order. When a subagent waits for a human the planner suspends without asking the
 * decision model, and decides on the answer when it is resumed. A crash during a decision call makes the subagent that had just completed run again, because the state is
 * saved only after the decision. A subagent whose output can be delayed, like a human-in-the-loop or an asynchronous
 * agent, must declare an output key, since the planner reads its answer from the scope: the decision following an
 * asynchronous subagent waits for its answer.
 * <p>
 * Each step is a greedy choice with no lookahead, so this planner fits the tasks where the most useful next step can
 * be told from the current state, not those that need a plan of several steps. The scores and the probabilities are
 * not calibrated in the same way by every decision model, so both thresholds must be tuned on the decision model in
 * use and tuned again when it changes. The state is untrusted input to the decision, so a crafted request or agent
 * output can influence it: this planner is not a security boundary.
 * <p>
 * A planner instance holds the state of a single invocation and is not thread-safe, so the agentic system must be
 * given a supplier creating a new instance at each invocation, for example
 * {@code () -> DecisionSchedulerPlanner.builder().decisionModel(decisionModel).goal("...").goalThreshold(0.5)
 * .activationThreshold(0.5).build()}, while the decision model can be shared.
 */
@Experimental
public class DecisionSchedulerPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(DecisionSchedulerPlanner.class);

    static final String GOAL_QUESTION_NAME = "goal-reached";
    static final int DEFAULT_MAX_INVOCATIONS = 10;

    private static final String DECISION_SCHEDULER_KEY_PREFIX = "decision-scheduler-";
    private static final String INVOCATIONS_STATE_KEY = DECISION_SCHEDULER_KEY_PREFIX + "invocations";
    private static final String SPENT_STATE_KEY = DECISION_SCHEDULER_KEY_PREFIX + "spent";
    private static final String CURRENT_STATE_KEY = DECISION_SCHEDULER_KEY_PREFIX + "current";
    private static final String PENDING_STATE_KEY = DECISION_SCHEDULER_KEY_PREFIX + "pending";

    private final DecisionModel decisionModel;
    private final String goal;
    private final double goalThreshold;
    private final double activationThreshold;
    private final int maxInvocations;

    private List<String> inputKeys;
    private final Map<String, Subagent> subagents = new LinkedHashMap<>();

    // the output key of the planner agent when a subagent writes it: the goal isn't asked before it is in the scope
    private String resultKey;

    // persisted to resume after a suspension or a crash: the number of subagents invoked so far, the ids of those
    // that ran and had no argument changed since, the id of the one being invoked (null between two steps), and
    // whether its answer was still pending, like an answer waited from a human
    private int invocations;
    private final Set<String> spent = new LinkedHashSet<>();
    private String current;
    private boolean pending;

    // the subagents left out of this invocation because their description cannot be read
    private final Set<String> unavailable = new HashSet<>();

    /** The levels on which the decision model grades the usefulness of a subagent, from the lowest to the highest. */
    enum Usefulness {
        USELESS,
        MARGINAL,
        USEFUL,
        ESSENTIAL;

        /** The levels as they are sent to the decision model. */
        static List<String> names() {
            return Arrays.stream(values()).map(Enum::name).toList();
        }
    }

    private record Subagent(AgentInstance agent, List<String> mandatoryArguments, Set<String> arguments) {

        static Subagent of(AgentInstance agent) {
            // framework-provided arguments, like @MemoryId, are not read from the state
            List<AgentArgument> arguments = agent.arguments().stream()
                    .filter(argument -> !argument.name().startsWith("@"))
                    .toList();
            return new Subagent(
                    agent,
                    arguments.stream()
                            .filter(argument -> argument.defaultValue() == null && !argument.isOptional())
                            .map(AgentArgument::name)
                            .toList(),
                    arguments.stream().map(AgentArgument::name).collect(Collectors.toSet()));
        }

        String id() {
            return agent.agentId();
        }
    }

    private DecisionSchedulerPlanner(Builder builder) {
        this.decisionModel = ensureNotNull(builder.decisionModel, "decisionModel");
        this.goal = ensureNotBlank(builder.goal, "goal");
        this.goalThreshold = validThreshold(builder.goalThreshold, "Goal threshold");
        this.activationThreshold = validThreshold(builder.activationThreshold, "Activation threshold");
        if (builder.maxInvocations < 1) {
            throw illegalArgument("Max invocations must be at least 1, but is: %s", builder.maxInvocations);
        }
        this.maxInvocations = builder.maxInvocations;
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

        // the id of a subagent is unique, unlike its name, and never the name of the goal question
        initPlanningContext.subagents().forEach(agent -> subagents.put(agent.agentId(), Subagent.of(agent)));

        String outputKey = plannerAgent.outputKey();
        boolean writtenByASubagent = !isNullOrBlank(outputKey)
                && subagents.values().stream()
                        .anyMatch(subagent -> outputKey.equals(subagent.agent().outputKey()));
        this.resultKey = writtenByASubagent ? outputKey : null;
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
        Subagent subagent = subagents.get(invocation.agentId());
        if (invocation.skipped()) {
            // a skipped agent didn't change the state, so it doesn't make other agents able to run again
            LOG.info("Subagent '{}' skipped ({})", subagent.id(), invocation.skipReason());
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
            throw new IllegalStateException("The answer of the subagent '" + subagent.id() + "' is not available "
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
                    .forEach(other -> spent.remove(other.id()));
        }
    }

    private Action decide(PlanningContext planningContext) {
        AgenticScope agenticScope = planningContext.agenticScope();
        boolean limitReached = invocations >= maxInvocations;
        List<Subagent> candidates = limitReached
                ? List.of()
                : subagents.values().stream()
                        .filter(subagent -> isCandidate(subagent, planningContext))
                        .toList();
        boolean askGoal = resultKey == null || agenticScope.hasState(resultKey);

        // at the limit only the goal is asked, to tell a goal reached with the last invocation from one missed
        if (candidates.isEmpty() && !(limitReached && askGoal)) {
            if (limitReached) {
                LOG.warn("Stopping after the maximum of {} invocations, before the result was produced", invocations);
            } else {
                LOG.info("Stopping after {} invocations, since no subagent can run", invocations);
            }
            return done();
        }

        DecisionRequest.Builder request = DecisionRequest.builder().inputFields(state(agenticScope));
        if (askGoal) {
            request.question(GOAL_QUESTION_NAME, YesNoQuestion.from(goalQuestionText(goal)));
        }
        candidates.forEach(
                candidate -> request.question(candidate.id(), usefulnessQuestion(candidate.agent(), goal)));
        DecisionResponse response = decisionModel.decide(request.build());

        Double goalProbability =
                askGoal ? response.yesNo(GOAL_QUESTION_NAME).probability() : null;
        if (goalProbability != null && goalProbability >= goalThreshold) {
            LOG.info("Goal reached after {} invocations, with probability {}", invocations, goalProbability);
            return done();
        }
        if (limitReached) {
            LOG.warn(
                    "Stopping after the maximum of {} invocations without reaching the goal, whose probability is {}",
                    invocations,
                    goalProbability);
            return done();
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        candidates.forEach(candidate ->
                scores.put(candidate.id(), response.scale(candidate.id()).mean() / Usefulness.ESSENTIAL.ordinal()));
        // the first declared candidate wins a tie
        Subagent best = candidates.get(0);
        for (Subagent candidate : candidates) {
            if (scores.get(candidate.id()) > scores.get(best.id())) {
                best = candidate;
            }
        }
        if (scores.get(best.id()) < activationThreshold) {
            LOG.info(
                    "Stopping after {} invocations, since no subagent is useful enough: scores {}, goal probability {}",
                    invocations,
                    scores,
                    goalProbability);
            return done();
        }

        spent.add(best.id());
        current = best.id();
        invocations++;
        LOG.info(
                "Invoking subagent '{}' (invocation #{}): scores {}, goal probability {}",
                best.id(),
                invocations,
                scores,
                goalProbability);
        return call(best.agent());
    }

    private boolean isCandidate(Subagent subagent, PlanningContext planningContext) {
        return !spent.contains(subagent.id())
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
        if (unavailable.contains(subagent.id())) {
            return false;
        }
        try {
            subagent.agent().description();
            return true;
        } catch (AgentUnavailableException e) {
            unavailable.add(subagent.id());
            LOG.warn(
                    "The subagent '{}' is left out of this invocation, because it is unavailable and its "
                            + "description cannot be read",
                    subagent.id(),
                    e);
            return false;
        }
    }

    static String goalQuestionText(String goal) {
        return "Has this goal been reached: " + goal + "?";
    }

    static String usefulnessQuestionText(String agentName, String agentDescription, String goal) {
        String description = isNullOrBlank(agentDescription) ? "" : " (" + agentDescription + ")";
        return "How useful would it be to run the agent '" + agentName + "'" + description
                + " now, to reach this goal: " + goal + "?";
    }

    private static ScaleQuestion usefulnessQuestion(AgentInstance agent, String goal) {
        return ScaleQuestion.from(usefulnessQuestionText(agent.name(), agent.description(), goal), Usefulness.names());
    }

    /**
     * The state sent to the decision model: the arguments of the planner agent as they are, and the outputs of the
     * subagents that are in the scope, converted to values that the decision model accepts.
     */
    private Map<String, Object> state(AgenticScope agenticScope) {
        Map<String, Object> state = new LinkedHashMap<>();
        inputKeys.forEach(key -> state.put(key, agenticScope.readState(key)));
        for (Subagent subagent : subagents.values()) {
            String outputKey = subagent.agent().outputKey();
            if (!isNullOrBlank(outputKey) && !state.containsKey(outputKey) && agenticScope.hasState(outputKey)) {
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
            savedSpent.forEach(id -> spent.add(id.toString()));
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
        private int maxInvocations = DEFAULT_MAX_INVOCATIONS;

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
        public Builder goalThreshold(double goalThreshold) {
            this.goalThreshold = goalThreshold;
            return this;
        }

        /**
         * Sets the minimum score, from 0 ("USELESS") to 1 ("ESSENTIAL"), for the most useful subagent to be
         * invoked: when no subagent reaches it the planner stops. Mandatory, strictly between 0 and 1.
         */
        public Builder activationThreshold(double activationThreshold) {
            this.activationThreshold = activationThreshold;
            return this;
        }

        /**
         * Sets the maximum number of subagent invocations, after which the planner stops with a warning. The
         * default is 10.
         */
        public Builder maxInvocations(int maxInvocations) {
            this.maxInvocations = maxInvocations;
            return this;
        }

        /**
         * @throws IllegalArgumentException if the decision model is {@code null}, if the goal is {@code null} or
         *         blank, if a threshold is not set or is not strictly between 0 and 1, or if the maximum number of
         *         invocations is less than 1
         */
        public DecisionSchedulerPlanner build() {
            return new DecisionSchedulerPlanner(this);
        }
    }
}
