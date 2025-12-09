package dev.langchain4j.agentic.observability;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.List;

@Internal
public class ComposedAgentListener implements AgentListener {

    private final List<AgentListener> listeners = new ArrayList<>();

    public ComposedAgentListener(AgentListener... listeners) {
        addListeners(listeners);
    }

    public void addListeners(AgentListener... listeners) {
        for (AgentListener listener : listeners) {
            if (listener instanceof ComposedAgentListener composed) {
                this.listeners.addAll(composed.listeners);
            } else {
                this.listeners.add(listener);
            }
        }
    }

    @Override
    public void beforeAgentInvocation(final AgentRequest agentRequest) {
        for (AgentListener listener : listeners) {
            listener.beforeAgentInvocation(agentRequest);
        }
    }

    @Override
    public void afterAgentInvocation(final AgentResponse agentResponse) {
        for (AgentListener listener : listeners) {
            listener.afterAgentInvocation(agentResponse);
        }
    }

    @Override
    public void onAgentInvocationError(final AgentInvocationError agentInvocationError) {
        for (AgentListener listener : listeners) {
            listener.onAgentInvocationError(agentInvocationError);
        }
    }

    @Override
    public void afterAgenticScopeCreated(final AgenticScope agenticScope) {
        for (AgentListener listener : listeners) {
            listener.afterAgenticScopeCreated(agenticScope);
        }
    }

    @Override
    public void beforeAgenticScopeDestroyed(final AgenticScope agenticScope) {
        for (AgentListener listener : listeners) {
            listener.beforeAgenticScopeDestroyed(agenticScope);
        }
    }
}
