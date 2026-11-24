package dev.langchain4j.agentic.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single place deciding whether an agent is active according to its activation condition. The
 * {@link AgentExecutor} enforces the condition when the agent is about to be invoked, while planners can consult it,
 * through {@link dev.langchain4j.agentic.planner.PlanningContext#isActive(AgentInstance)}, to avoid selecting an
 * inactive agent.
 * <p>
 * A composite agent whose condition held is marked as activated in the scope until it completes, so that, when it is
 * resumed after a suspension or a crash, it is still active even if its own subagents changed the state its condition
 * reads. Leaf agents are never marked, since they never partially complete.
 */
@Internal
public final class ActivationConditions {

    private static final Logger LOG = LoggerFactory.getLogger(ActivationConditions.class);

    private static final String ACTIVATION_MARKER_PREFIX = "__activated_";

    private ActivationConditions() {}

    /**
     * Tells whether the agent would run if it were invoked now: it has no condition, it is a composite agent already
     * activated, or its condition holds. A condition throwing an exception is considered to hold, so that the failure
     * is raised, and handled by the error handler, when the agent is invoked.
     */
    public static boolean isActive(AgentInstance agent, AgenticScope agenticScope) {
        AgentCondition condition = agent.activationCondition();
        if (condition == null || isActivated(agent, agenticScope)) {
            return true;
        }
        try {
            return condition.test(agenticScope);
        } catch (RuntimeException e) {
            LOG.debug(
                    "Activation condition of agent '{}' failed while planning, it will fail again when invoked",
                    agent.name(),
                    e);
            return true;
        }
    }

    static boolean isActivated(AgentInstance agent, AgenticScope agenticScope) {
        return !agent.leaf() && agenticScope.hasState(activationMarkerKey(agent));
    }

    static void markActivated(AgentInstance agent, AgenticScope agenticScope) {
        if (!agent.leaf()) {
            agenticScope.writeState(activationMarkerKey(agent), true);
        }
    }

    static void clearActivation(AgentInstance agent, AgenticScope agenticScope) {
        if (agent.activationCondition() != null && !agent.leaf()) {
            agenticScope.writeState(activationMarkerKey(agent), null);
        }
    }

    private static String activationMarkerKey(AgentInstance agent) {
        return ACTIVATION_MARKER_PREFIX + agent.agentId();
    }
}
