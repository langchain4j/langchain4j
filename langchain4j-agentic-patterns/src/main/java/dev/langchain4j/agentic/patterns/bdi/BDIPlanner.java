package dev.langchain4j.agentic.patterns.bdi;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class BDIPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(BDIPlanner.class);

    private static final int DEFAULT_MAX_INVOCATIONS = 50;

    private final List<Desire> desires;
    private final int maxInvocations;

    private Map<Class<?>, AgentInstance> agentsByType;
    private final Map<String, Integer> desireProgress = new HashMap<>();
    private Desire currentDesire;
    private List<AgentInstance> currentIntention;
    private int intentionCursor;
    private int invocationCounter;

    public BDIPlanner(List<Desire> desires) {
        this(desires, DEFAULT_MAX_INVOCATIONS);
    }

    public BDIPlanner(List<Desire> desires, int maxInvocations) {
        this.desires = desires;
        this.maxInvocations = maxInvocations;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        this.agentsByType = initPlanningContext.subagents().stream()
                .collect(toMap(AgentInstance::type, a -> a, (a, b) -> {
                    throw new IllegalArgumentException(
                            "BDI desires reference agents by type, so each agent type must be unique. " +
                            "Duplicate agent type: " + a.type().getName());
                }));
        for (Desire desire : desires) {
            for (Class<?> agentType : desire.agentTypes()) {
                if (!agentsByType.containsKey(agentType)) {
                    throw new IllegalArgumentException(
                            "Desire '" + desire.name() + "' references unknown agent type: " + agentType.getName());
                }
            }
        }
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        return deliberate(planningContext.agenticScope());
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        AgenticScope scope = planningContext.agenticScope();

        if (currentDesire != null) {
            boolean preempted = desires.stream()
                    .filter(d -> d != currentDesire)
                    .filter(d -> d.priority() > currentDesire.priority())
                    .filter(d -> d.achievable().test(scope))
                    .anyMatch(d -> !d.satisfied().test(scope));

            if (preempted) {
                desireProgress.put(currentDesire.name(), intentionCursor + 1);
                LOG.info("Preempting desire '{}' for a higher-priority desire", currentDesire.name());
                return deliberate(scope);
            }

            if (currentDesire.achievable().test(scope) && !currentDesire.satisfied().test(scope)) {
                intentionCursor++;
                if (intentionCursor < currentIntention.size()) {
                    return dispatch(currentIntention.get(intentionCursor));
                }
                throw new IllegalStateException(
                        "Desire '" + currentDesire.name() + "' is still unsatisfied after its entire intention " +
                        "completed (" + currentIntention.size() + " agents). Check that the intention's agents " +
                        "write the state keys required by the desire's satisfied predicate.");
            }
        }

        return deliberate(scope);
    }

    private Action dispatch(AgentInstance agent) {
        if (invocationCounter >= maxInvocations) {
            throw new IllegalStateException(
                    "Maximum invocations (" + maxInvocations + ") reached with unsatisfied desires. " +
                    "Increase maxInvocations or check desire predicates.");
        }
        invocationCounter++;
        return call(agent);
    }

    private Action deliberate(AgenticScope scope) {
        return desires.stream()
                .filter(d -> d.achievable().test(scope))
                .filter(d -> !d.satisfied().test(scope))
                .max(Comparator.comparingInt(Desire::priority))
                .map(desire -> {
                    currentDesire = desire;
                    currentIntention = desire.agentTypes().stream()
                            .map(agentsByType::get)
                            .toList();
                    intentionCursor = desireProgress.getOrDefault(desire.name(), 0);
                    LOG.info("Committing to desire '{}' (priority {}) at step {}/{}",
                            desire.name(), desire.priority(), intentionCursor + 1, currentIntention.size());
                    return dispatch(currentIntention.get(intentionCursor));
                })
                .orElseGet(() -> {
                    LOG.info("All desires satisfied or none achievable after {} invocations", invocationCounter);
                    return done();
                });
    }

    @Override
    public Map<String, Object> executionState() {
        return Map.of("invocationCounter", invocationCounter);
    }

    /**
     * BDIPlanner does not persist intention cursor or current desire because {@link #firstAction(PlanningContext)}
     * re-deliberates from the current scope state. On recovery, completed agents' outputs are already
     * in scope, so satisfied desires are skipped and the planner re-selects the correct desire with
     * only the remaining agents to execute. Persisting the cursor would risk a stale value pointing
     * beyond the bounds of a recomputed intention.
     */
    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        Object savedCounter = state.get("invocationCounter");
        if (savedCounter instanceof Number n) {
            this.invocationCounter = n.intValue();
        }
    }
}
