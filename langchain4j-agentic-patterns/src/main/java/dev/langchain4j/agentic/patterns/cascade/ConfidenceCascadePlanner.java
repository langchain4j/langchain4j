package dev.langchain4j.agentic.patterns.cascade;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
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
import dev.langchain4j.model.decision.request.YesNoQuestion;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A planner trying its subagents one at a time, from the cheapest to the most capable, until one of them gives an
 * answer that a {@link DecisionModel} accepts. The subagents are the tiers of the cascade, in declaration order: for
 * example an agent on a small model, the same agent on a large model, and finally a human.
 * <p>
 * After each tier answers, the planner asks the decision model the yes/no question "Does the answer fully and
 * correctly address the request?", with an input made of the arguments of the cascade agent itself, read from the
 * {@link AgenticScope}, plus the answer of the tier under the {@code candidateAnswer} field. If the probability of
 * "yes" is at least the acceptance threshold the answer is returned as the result of the cascade, otherwise the next
 * tier is tried. The last tier is never graded: its answer is returned anyway. More generally, the answer of a tier
 * is returned without grading it when no later tier is active, according to the activation conditions of the
 * subagents, and a tier that is skipped when invoked is passed over without a grade. When the remaining tiers are
 * all skipped, the result is the latest answer obtained, or {@code null} if no tier answered.
 * <p>
 * An optional acceptance criterion describes when the answer should be accepted, for instance "The answer gives
 * concrete steps and does not ask the user to contact support", and is sent to the decision model as the
 * {@link YesNoQuestion#yesWhen() yesWhen} description of the question. The arguments are sent as they are, so they
 * can be strings, numbers, booleans, maps and lists of them, or {@link Content}s such as images, and an argument of
 * any other type is rejected by the decision model with an error explaining which types are supported: the
 * application decides what is sent to the provider of the decision model. The answer is sent as it is when it is
 * such a value, and converted to maps and lists through a JSON round trip otherwise, so a tier can return a typed
 * value.
 * <p>
 * The planner saves the tier it is trying as its execution state, so that after a suspension, for instance because a
 * tier is waiting for a human, or after a crash, the cascade resumes from that tier and does not pay again for the
 * cheaper ones. A crash during the grading call itself makes the tier being graded run again, because the state is
 * saved only after the grade. A tier whose output can be delayed, like a human-in-the-loop agent or an asynchronous
 * agent, must declare an output key, since the planner reads its answer from the scope: the grade of an asynchronous
 * tier waits for its answer, and the answer of a human is graded when the cascade is resumed.
 * <p>
 * The grade is a relevance and completeness signal, not a proof of correctness: a decision model can judge whether
 * an answer addresses the request, but it cannot verify facts. Probabilities are not calibrated in the same way by
 * every decision model, so the acceptance threshold must be tuned on the decision model in use and tuned again when
 * it changes. The request and the answers are untrusted input to the grade, so a crafted request or answer can
 * influence it: the cascade trades cost against quality, it is not a security or review boundary. A failure of the
 * decision model fails the invocation of the cascade.
 * <p>
 * A planner instance holds the state of a single invocation and is not thread-safe, so the agentic system must be
 * given a supplier creating a new instance at each invocation, for example
 * {@code () -> new ConfidenceCascadePlanner(decisionModel, 0.5)}, while the decision model can be shared.
 */
@Experimental
public class ConfidenceCascadePlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(ConfidenceCascadePlanner.class);

    static final String QUESTION_NAME = "accepted";
    static final String QUESTION_TEXT = "Does the answer fully and correctly address the request?";
    static final String ANSWER_KEY = "candidateAnswer";

    private static final String CONFIDENCE_CASCADE_KEY_PREFIX = "confidence-cascade-";
    private static final String TIER_STATE_KEY = CONFIDENCE_CASCADE_KEY_PREFIX + "tier";
    private static final String TIER_NAME_STATE_KEY = CONFIDENCE_CASCADE_KEY_PREFIX + "tier-name";
    private static final String PENDING_STATE_KEY = CONFIDENCE_CASCADE_KEY_PREFIX + "pending";
    private static final String ANSWERED_STATE_KEY = CONFIDENCE_CASCADE_KEY_PREFIX + "answered";

    private final DecisionModel decisionModel;
    private final double acceptanceThreshold;
    private final YesNoQuestion question;

    private List<String> inputKeys;
    private List<AgentInstance> tiers;

    // persisted to resume after a suspension or a crash: the index of the tier being tried (-1 before the first one
    // is called), whether its answer was still pending, like an answer waited from a human, and the index of the last
    // tier that answered (-1 if none)
    private int tier = -1;
    private boolean pending;
    private int answered = -1;

    // the name of the tier being tried when the execution state was saved, checked on resume
    private String restoredTierName;

    // the latest answer obtained, returned when the remaining tiers are all skipped
    private Object latestAnswer;

    /**
     * Creates a cascade accepting the answer of a tier when the probability that it fully and correctly addresses
     * the request is at least the given threshold.
     * <p>
     * When the cascade is invoked, before calling the first tier, it fails with an {@link IllegalArgumentException}
     * if the cascade agent is not a typed agent interface with at least one argument to send to the decision model,
     * if one of its arguments is named {@code candidateAnswer}, or if it has fewer than two subagents.
     *
     * @param decisionModel the decision model grading the answer of each tier
     * @param acceptanceThreshold the minimum probability of "yes" for an answer to be accepted, strictly between 0
     *        and 1
     * @throws IllegalArgumentException if {@code decisionModel} is {@code null}, or if {@code acceptanceThreshold}
     *         is not strictly between 0 and 1
     */
    public ConfidenceCascadePlanner(DecisionModel decisionModel, double acceptanceThreshold) {
        this(decisionModel, acceptanceThreshold, null);
    }

    /**
     * Creates a cascade accepting the answer of a tier when the probability that it fully and correctly addresses
     * the request, according to the given acceptance criterion, is at least the given threshold.
     * <p>
     * When the cascade is invoked, before calling the first tier, it fails with an {@link IllegalArgumentException}
     * if the cascade agent is not a typed agent interface with at least one argument to send to the decision model,
     * if one of its arguments is named {@code candidateAnswer}, or if it has fewer than two subagents.
     *
     * @param decisionModel the decision model grading the answer of each tier
     * @param acceptanceThreshold the minimum probability of "yes" for an answer to be accepted, strictly between 0
     *        and 1
     * @param acceptanceCriterion a description of when the answer should be accepted, sent to the decision model as
     *        the {@code yesWhen} description of the grading question, or {@code null} for none
     * @throws IllegalArgumentException if {@code decisionModel} is {@code null}, if {@code acceptanceThreshold} is
     *         not strictly between 0 and 1, or if {@code acceptanceCriterion} is blank
     */
    public ConfidenceCascadePlanner(
            DecisionModel decisionModel, double acceptanceThreshold, String acceptanceCriterion) {
        this.decisionModel = ensureNotNull(decisionModel, "decisionModel");
        if (!(acceptanceThreshold > 0.0 && acceptanceThreshold < 1.0)) {
            throw illegalArgument(
                    "Acceptance threshold must be greater than 0.0 and less than 1.0, but is: %s", acceptanceThreshold);
        }
        this.acceptanceThreshold = acceptanceThreshold;
        if (acceptanceCriterion != null && acceptanceCriterion.isBlank()) {
            throw illegalArgument("Acceptance criterion must not be blank, pass null for no criterion");
        }
        this.question = YesNoQuestion.builder()
                .text(QUESTION_TEXT)
                .yesWhen(acceptanceCriterion)
                .build();
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        // framework-provided arguments, like @MemoryId, are not part of the request
        this.inputKeys = initPlanningContext.plannerAgent().arguments().stream()
                .map(AgentArgument::name)
                .filter(name -> !name.startsWith("@"))
                .toList();
        if (inputKeys.isEmpty()) {
            throw new IllegalArgumentException("ConfidenceCascadePlanner requires a typed agent interface whose "
                    + "method arguments are the request graded together with the answer of each tier");
        }
        if (inputKeys.contains(ANSWER_KEY)) {
            throw new IllegalArgumentException("ConfidenceCascadePlanner sends the answer of each tier to the "
                    + "decision model as the '" + ANSWER_KEY + "' field, so the cascade agent cannot have an "
                    + "argument with that name");
        }
        this.tiers = List.copyOf(initPlanningContext.subagents());
        if (tiers.size() < 2) {
            throw new IllegalArgumentException("ConfidenceCascadePlanner requires at least 2 subagents, declared "
                    + "from the cheapest to the most capable, but has " + tiers.size());
        }
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        if (tier < 0) {
            tier = 0;
            return call(tiers.get(0));
        }
        // resuming after a suspension or a crash
        ensureSameTiers();
        if (!pending) {
            return call(tiers.get(tier));
        }
        AgentInstance current = tiers.get(tier);
        Object answer = scopeOutput(current, planningContext.agenticScope());
        if (answer instanceof DelayedResponse<?> delayedResponse) {
            if (!delayedResponse.isDone()) {
                // resumed before the answer arrived
                return suspend();
            }
            answer = delayedResponse.result();
        }
        pending = false;
        return evaluate(planningContext, current, answer);
    }

    private void ensureSameTiers() {
        if (tier >= tiers.size()
                || (restoredTierName != null
                        && !restoredTierName.equals(tiers.get(tier).name()))) {
            throw new IllegalStateException("Cannot resume ConfidenceCascadePlanner at the tier '" + restoredTierName
                    + "' in position " + tier + ", since the subagents of the cascade changed after its execution "
                    + "state was saved");
        }
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        AgentInvocation invocation = planningContext.previousAgentInvocation();
        AgentInstance current = tiers.get(tier);
        if (invocation.skipped()) {
            LOG.info("Tier '{}' skipped ({}), moving on to the next one", current.name(), invocation.skipReason());
            return tier + 1 < tiers.size() ? call(tiers.get(++tier)) : done(fallbackAnswer(planningContext));
        }

        Object answer = invocation.output();
        if (scopeOutput(current, planningContext.agenticScope()) instanceof DelayedResponse<?> delayedResponse) {
            if (!delayedResponse.isDone() && delayedResponse instanceof SuspendedResponse<?>) {
                // the answer, for instance from a human, will be graded when the cascade is resumed
                pending = true;
                return suspend();
            }
            // an asynchronous tier: the grade has to wait for its answer
            answer = planningContext.agenticScope().readState(current.outputKey());
        } else if (DelayedResponse.PENDING.equals(answer)) {
            throw new IllegalStateException("The answer of the tier '" + current.name() + "' is not available yet, "
                    + "but the tier has no output key where ConfidenceCascadePlanner can read it when it arrives: "
                    + "declare an output key on every tier whose answer can be delayed");
        }
        return evaluate(planningContext, current, answer);
    }

    private Action evaluate(PlanningContext planningContext, AgentInstance current, Object answer) {
        latestAnswer = answer;
        answered = tier;

        boolean lastActiveTier = tiers.subList(tier + 1, tiers.size()).stream().noneMatch(planningContext::isActive);
        if (lastActiveTier) {
            LOG.info(
                    "Returning the answer of the tier '{}' without grading it, since no later tier is active",
                    current.name());
            return done(answer);
        }

        double probability = acceptance(planningContext.agenticScope(), answer);
        if (probability >= acceptanceThreshold) {
            LOG.info("Accepting the answer of the tier '{}', graded {}", current.name(), probability);
            return done(answer);
        }
        AgentInstance next = tiers.get(++tier);
        LOG.info(
                "Rejecting the answer of the tier '{}', graded {}, escalating to '{}'",
                current.name(),
                probability,
                next.name());
        return call(next);
    }

    private double acceptance(AgenticScope agenticScope, Object answer) {
        Map<String, Object> input = new LinkedHashMap<>();
        inputKeys.forEach(key -> input.put(key, agenticScope.readState(key)));
        input.put(ANSWER_KEY, answerValue(answer));
        return decisionModel
                .decide(DecisionRequest.builder()
                        .inputFields(input)
                        .question(QUESTION_NAME, question)
                        .build())
                .yesNo(QUESTION_NAME)
                .probability();
    }

    /**
     * The answer as the decision model accepts it: a plain value is sent as it is, any other object is converted to
     * maps and lists through a JSON round trip.
     */
    private static Object answerValue(Object answer) {
        return isPlain(answer) ? answer : Json.fromJson(Json.toJson(answer), Object.class);
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
            return map.values().stream().allMatch(ConfidenceCascadePlanner::isPlain);
        }
        return value instanceof Collection<?> collection
                && collection.stream().allMatch(ConfidenceCascadePlanner::isPlain);
    }

    /** The latest answer obtained, read from the scope after a restore. */
    private Object fallbackAnswer(PlanningContext planningContext) {
        if (latestAnswer == null && answered >= 0) {
            Object scopeOutput = scopeOutput(tiers.get(answered), planningContext.agenticScope());
            return scopeOutput instanceof DelayedResponse<?> delayedResponse ? delayedResponse.result() : scopeOutput;
        }
        return latestAnswer;
    }

    private static Object scopeOutput(AgentInstance agent, AgenticScope agenticScope) {
        String outputKey = agent.outputKey();
        return outputKey == null ? null : agenticScope.state().get(outputKey);
    }

    @Override
    public Map<String, Object> executionState() {
        if (tier < 0) {
            return Map.of();
        }
        Map<String, Object> state = new HashMap<>();
        state.put(TIER_STATE_KEY, tier);
        state.put(TIER_NAME_STATE_KEY, tiers.get(tier).name());
        state.put(PENDING_STATE_KEY, pending);
        if (answered >= 0) {
            state.put(ANSWERED_STATE_KEY, answered);
        }
        return state;
    }

    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        if (state.get(TIER_STATE_KEY) instanceof Number savedTier) {
            this.tier = savedTier.intValue();
        }
        if (state.get(PENDING_STATE_KEY) instanceof Boolean savedPending) {
            this.pending = savedPending;
        }
        if (state.get(TIER_NAME_STATE_KEY) instanceof String savedTierName) {
            this.restoredTierName = savedTierName;
        }
        if (state.get(ANSWERED_STATE_KEY) instanceof Number savedAnswered) {
            this.answered = savedAnswered.intValue();
        }
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.SEQUENCE;
    }
}
