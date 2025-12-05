package dev.langchain4j.agentic.observability;

import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.Map;

public class ListenerNotifierUtil {
    private ListenerNotifierUtil() { }

    public static void beforeAgentInvocation(AgenticListener listener, AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs) {
        if (listener != null) {
            listener.beforeAgentInvocation(new AgentRequest(agenticScope, agent, inputs));
        }
    }

    public static void afterAgentInvocation(AgenticListener listener, AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs, Object output) {
        if (listener != null) {
            listener.afterAgentInvocation(new AgentResponse(agenticScope, agent, inputs, output));
        }
    }

    public static void agentError(AgenticListener listener, AgenticScope agenticScope, AgentInstance agent, Map<String, Object> inputs, Throwable error) {
        if (listener != null) {
            listener.onAgentInvocationError(new AgentInvocationError(agenticScope, agent, inputs, error));
        }
    }

    public static void onAgenticScopeCreated(AgenticListener listener, AgenticScope agenticScope) {
        if (listener != null) {
            listener.onAgenticScopeCreated(agenticScope);
        }
    }

    public static void onAgenticScopeDestroyed(AgenticListener listener, AgenticScope agenticScope) {
        if (listener != null) {
            listener.onAgenticScopeDestroyed(agenticScope);
        }
    }
}
