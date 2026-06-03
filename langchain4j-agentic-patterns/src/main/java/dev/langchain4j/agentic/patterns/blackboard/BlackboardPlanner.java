package dev.langchain4j.agentic.patterns.blackboard;

import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toMap;

/**
 * A blackboard planner that activates agents based on data availability in the shared scope.
 * <p>
 * Agents are knowledge sources that post partial results to the {@link AgenticScope} (the blackboard).
 * After each agent completes, the planner inspects the blackboard and activates whichever single agent
 * can contribute next. When multiple agents are ready, a {@link ConflictResolutionStrategy} determines
 * which one fires; if no strategy is provided, declaration order is used.
 * <p>
 * The planner terminates when:
 * <ul>
 *   <li>The goal predicate is satisfied (by default, when the planner's outputKey is present in scope)</li>
 *   <li>No agent can fire (quiescence)</li>
 *   <li>The maximum number of invocations is reached</li>
 * </ul>
 */
public class BlackboardPlanner implements Planner {

    private static final Logger LOG = LoggerFactory.getLogger(BlackboardPlanner.class);

    private static final int DEFAULT_MAX_INVOCATIONS = 50;

    private Predicate<AgenticScope> goalPredicate;
    private final ConflictResolutionStrategy conflictResolutionStrategy;
    private final int maxInvocations;

    private Map<String, AgentActivator> agentActivators;
    private int invocationCounter = 0;

    public BlackboardPlanner() {
        this(null, DEFAULT_MAX_INVOCATIONS, ConflictResolutionStrategy.DECLARATION_ORDER);
    }

    public BlackboardPlanner(Predicate<AgenticScope> goalPredicate) {
        this(goalPredicate, DEFAULT_MAX_INVOCATIONS, ConflictResolutionStrategy.DECLARATION_ORDER);
    }

    public BlackboardPlanner(ConflictResolutionStrategy conflictResolutionStrategy) {
        this(null, DEFAULT_MAX_INVOCATIONS, conflictResolutionStrategy);
    }

    public BlackboardPlanner(Predicate<AgenticScope> goalPredicate, int maxInvocations) {
        this(goalPredicate, maxInvocations, ConflictResolutionStrategy.DECLARATION_ORDER);
    }

    public BlackboardPlanner(Predicate<AgenticScope> goalPredicate, ConflictResolutionStrategy conflictResolutionStrategy) {
        this(goalPredicate, DEFAULT_MAX_INVOCATIONS, conflictResolutionStrategy);
    }

    public BlackboardPlanner(Predicate<AgenticScope> goalPredicate, int maxInvocations, ConflictResolutionStrategy conflictResolutionStrategy) {
        this.goalPredicate = goalPredicate;
        this.maxInvocations = maxInvocations;
        this.conflictResolutionStrategy = conflictResolutionStrategy;
    }

    @Override
    public void init(InitPlanningContext initPlanningContext) {
        if (goalPredicate == null) {
            String outputKey = initPlanningContext.plannerAgent().outputKey();
            this.goalPredicate = scope -> scope.hasState(outputKey);
        }
        this.agentActivators = initPlanningContext.subagents().stream()
                .collect(toMap(AgentInstance::agentId, AgentActivator::new, (a, b) -> a, LinkedHashMap::new));
    }

    @Override
    public Action firstAction(PlanningContext planningContext) {
        AgenticScope scope = planningContext.agenticScope();
        if (goalPredicate.test(scope)) {
            return done();
        }
        return selectAndCall(scope);
    }

    @Override
    public Action nextAction(PlanningContext planningContext) {
        AgenticScope scope = planningContext.agenticScope();

        AgentActivator lastExecutedAgent = agentActivators.get(planningContext.previousAgentInvocation().agentId());
        if (lastExecutedAgent != null) {
            agentActivators.values().forEach(a -> a.onStateChanged(lastExecutedAgent.agent.outputKey()));
        }

        if (goalPredicate.test(scope)) {
            LOG.info("Goal predicate satisfied after {} invocations", invocationCounter);
            return done();
        }

        if (invocationCounter >= maxInvocations) {
            LOG.warn("Maximum invocations ({}) reached without satisfying goal", maxInvocations);
            return done();
        }

        return selectAndCall(scope);
    }

    private Action selectAndCall(AgenticScope scope) {
        List<AgentActivator> ready = agentActivators.values().stream()
                .filter(a -> a.canActivate(scope))
                .toList();

        if (ready.isEmpty()) {
            LOG.info("No agents can fire — blackboard quiescent after {} invocations", invocationCounter);
            return done();
        }

        AgentActivator selected = selectActivator(ready, scope);
        if (selected == null) {
            LOG.info("No agents can fire — blackboard quiescent after {} invocations", invocationCounter);
            return done();
        }
        selected.markFired();
        invocationCounter++;

        LOG.info("Activating agent '{}' (invocation #{})", selected.agent.name(), invocationCounter);
        return call(selected.agent);
    }

    private AgentActivator selectActivator(List<AgentActivator> ready, AgenticScope scope) {
        List<AgentInstance> candidates = ready.stream().map(a -> a.agent).toList();
        AgentInstance selected = conflictResolutionStrategy.resolve(scope, candidates);
        return selected == null ? null : ready.stream()
                .filter(a -> a.agent == selected)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Agent not found in ready list"));
    }

    @Override
    public Map<String, Object> executionState() {
        return Map.of("invocationCounter", invocationCounter);
    }

    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        Object savedCounter = state.get("invocationCounter");
        if (savedCounter instanceof Number n) {
            this.invocationCounter = n.intValue();
        }
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.STAR;
    }

    private static class AgentActivator {
        private final AgentInstance agent;
        private final List<String> argumentNames;

        private boolean shouldExecute = true;

        AgentActivator(AgentInstance agent) {
            this.agent = agent;
            this.argumentNames = agent.arguments().stream().map(AgentArgument::name).toList();
        }

        private boolean canActivate(AgenticScope agenticScope) {
            return shouldExecute && argumentNames.stream().allMatch(agenticScope::hasState);
        }

        private void markFired() {
            shouldExecute = false;
        }

        private void onStateChanged(String state) {
            if (argumentNames.contains(state)) {
                shouldExecute = true;
            }
        }
    }
}
