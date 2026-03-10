package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class ListenerNotifierUtil {

    private static final Logger LOG = LoggerFactory.getLogger(ListenerNotifierUtil.class);

    private ListenerNotifierUtil() { }

    public static void beforeAgentInvocation(AgentListener listener, AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs) {
        if (listener != null) {
            try {
                listener.beforeAgentInvocation(new AgentRequest(agenticScope, agent, inputs));
            } catch (Exception e) {
                LOG.error("beforeAgentInvocation listener for agent " + agent.name() + " failed: " + e.getMessage(), e);
            }
        }
    }

    public static void afterAgentInvocation(AgentListener listener, AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs, Object output) {
        if (listener != null) {
            try {
                listener.afterAgentInvocation(new AgentResponse(agenticScope, agent, inputs, output));
            } catch (Exception e) {
                LOG.error("afterAgentInvocation listener for agent " + agent.name() + " failed: " + e.getMessage(), e);
            }
        }
    }

    public static void agentError(AgentListener listener, AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs, Throwable error) {
        if (listener != null) {
            try {
                listener.onAgentInvocationError(new AgentInvocationError(agenticScope, agent, inputs, error));
            } catch (Exception e) {
                LOG.error("agentError listener for agent " + agent.name() + " failed: " + e.getMessage(), e);
            }
        }
    }

    public static void afterAgenticScopeCreated(AgentListener listener, AgenticScope agenticScope) {
        if (listener != null) {
            try {
                listener.afterAgenticScopeCreated(agenticScope);
            } catch (Exception e) {
                LOG.error("afterAgenticScopeCreated listener failed: " + e.getMessage(), e);
            }
        }
    }

    public static void beforeAgenticScopeDestroyed(AgentListener listener, AgenticScope agenticScope) {
        if (listener != null) {
            try {
                listener.beforeAgenticScopeDestroyed(agenticScope);
            } catch (Exception e) {
                LOG.error("beforeAgenticScopeDestroyed listener failed: " + e.getMessage(), e);
            }
        }
    }
}
