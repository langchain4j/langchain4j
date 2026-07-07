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
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class BDIPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(BDIPlanner.class);

    private static final int DEFAULT_MAX_INVOCATIONS = 50;

    private final List<Desire> desires;
    private final int maxInvocations;

    private Map<Class<?>, AgentInstance> agentsByType;
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
                .collect(toMap(AgentInstance::type, a -> a));
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
        invocationCounter++;

        if (invocationCounter >= maxInvocations) {
            LOG.warn("Maximum invocations ({}) reached", maxInvocations);
            return done();
        }

        if (currentDesire != null) {
            // Preemption: check if a higher-priority desire should take over
            boolean preempted = desires.stream()
                    .filter(d -> d != currentDesire)
                    .filter(d -> d.priority() > currentDesire.priority())
                    .filter(d -> d.achievable().test(scope))
                    .anyMatch(d -> !d.satisfied().test(scope));

            if (preempted) {
                LOG.info("Preempting desire '{}' for a higher-priority desire", currentDesire.name());
                return deliberate(scope);
            }

            // Continue current intention if desire is still viable
            if (currentDesire.achievable().test(scope) && !currentDesire.satisfied().test(scope)) {
                intentionCursor++;
                if (intentionCursor < currentIntention.size()) {
                    return call(currentIntention.get(intentionCursor));
                }
            }
        }

        return deliberate(scope);
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
                    intentionCursor = 0;
                    invocationCounter++;
                    LOG.info("Committing to desire '{}' (priority {})", desire.name(), desire.priority());
                    return call(currentIntention.get(0));
                })
                .orElseGet(() -> {
                    LOG.info("All desires satisfied or none achievable after {} invocations", invocationCounter);
                    return done();
                });
    }

    @Override
    public Map<String, Object> executionState() {
        return Map.of(
                "invocationCounter", invocationCounter,
                "intentionCursor", intentionCursor,
                "currentDesireName", currentDesire != null ? currentDesire.name() : "");
    }

    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        Object savedCounter = state.get("invocationCounter");
        if (savedCounter instanceof Number n) {
            this.invocationCounter = n.intValue();
        }
        Object savedCursor = state.get("intentionCursor");
        if (savedCursor instanceof Number n) {
            this.intentionCursor = n.intValue();
        }
        Object savedDesireName = state.get("currentDesireName");
        if (savedDesireName instanceof String name && !name.isEmpty()) {
            this.currentDesire = desires.stream()
                    .filter(d -> d.name().equals(name))
                    .findFirst()
                    .orElse(null);
            if (currentDesire != null) {
                this.currentIntention = currentDesire.agentTypes().stream()
                        .map(agentsByType::get)
                        .toList();
            }
        }
    }
}
