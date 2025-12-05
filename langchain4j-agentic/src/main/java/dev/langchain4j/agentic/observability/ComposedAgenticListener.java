package dev.langchain4j.agentic.observability;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.List;

@Internal
public class ComposedAgenticListener implements AgenticListener {

    private final List<AgenticListener> listeners = new ArrayList<>();

    public ComposedAgenticListener(AgenticListener... listeners) {
        addListeners(listeners);
    }

    public void addListeners(AgenticListener... listeners) {
        for (AgenticListener listener : listeners) {
            if (listener instanceof ComposedAgenticListener composed) {
                this.listeners.addAll(composed.listeners);
            } else {
                this.listeners.add(listener);
            }
        }
    }

    @Override
    public void beforeAgentInvocation(final AgentRequest agentRequest) {
        for (AgenticListener listener : listeners) {
            listener.beforeAgentInvocation(agentRequest);
        }
    }

    @Override
    public void afterAgentInvocation(final AgentResponse agentResponse) {
        for (AgenticListener listener : listeners) {
            listener.afterAgentInvocation(agentResponse);
        }
    }

    @Override
    public void onAgentInvocationError(final AgentInvocationError agentInvocationError) {
        for (AgenticListener listener : listeners) {
            listener.onAgentInvocationError(agentInvocationError);
        }
    }

    @Override
    public void onAgenticScopeCreated(final AgenticScope agenticScope) {
        for (AgenticListener listener : listeners) {
            listener.onAgenticScopeCreated(agenticScope);
        }
    }

    @Override
    public void onAgenticScopeDestroyed(final AgenticScope agenticScope) {
        for (AgenticListener listener : listeners) {
            listener.onAgenticScopeDestroyed(agenticScope);
        }
    }
}
