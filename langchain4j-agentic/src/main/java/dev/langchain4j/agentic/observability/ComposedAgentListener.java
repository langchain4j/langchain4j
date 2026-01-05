package dev.langchain4j.agentic.observability;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Internal
public class ComposedAgentListener implements AgentListener {

    private final List<AgentListener> listeners;

    private ComposedAgentListener(List<AgentListener> listeners) {
        this.listeners = listeners;
    }

    public ComposedAgentListener(AgentListener... listeners) {
        this(collectListeners(listeners));
    }

    private static List<AgentListener> collectListeners(AgentListener... listeners) {
        List<AgentListener> collectedListeners = new ArrayList<>();
        for (AgentListener listener : listeners) {
            if (listener == null) {
                continue;
            }
            if (listener instanceof ComposedAgentListener composed) {
                collectedListeners.addAll(composed.listeners);
            } else {
                collectedListeners.add(listener);
            }
        }
        return collectedListeners;
    }

    public void addListener(AgentListener listener) {
        if (listener instanceof ComposedAgentListener composed) {
            this.listeners.addAll(composed.listeners);
        } else {
            this.listeners.add(listener);
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
    public void afterToolExecution(ToolExecution toolExecution) {
        for (AgentListener listener : listeners) {
            listener.afterToolExecution(toolExecution);
        }
    }

    @Override
    public void beforeToolExecution(BeforeToolExecution beforeToolExecution) {
        for (AgentListener listener : listeners) {
            listener.beforeToolExecution(beforeToolExecution);
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

    public static AgentListener composeWithInherited(AgentListener localListener, AgentListener parentListener) {
        if (parentListener == null) {
            return localListener;
        }
        List<AgentListener> listeners = new ArrayList<>();
        addInherited(listeners, parentListener);
        addAll(listeners, localListener);
        return composedListener(listeners);
    }

    private static void addAll(List<AgentListener> existingListeners, AgentListener newListener) {
        add(existingListeners, newListener, l -> true);
    }

    private static void addInherited(List<AgentListener> existingListeners, AgentListener newListener) {
        add(existingListeners, newListener, AgentListener::inheritedBySubagents);
    }

    private static void add(List<AgentListener> existingListeners, AgentListener newListener, Predicate<AgentListener> filter) {
        if (newListener == null) {
            return;
        }
        if (newListener instanceof ComposedAgentListener composed) {
            existingListeners.addAll(composed.listeners.stream().filter(filter).toList());
        } else {
            if (filter.test(newListener)) {
                existingListeners.add(newListener);
            }
        }
    }

    private static AgentListener composedListener(List<AgentListener> inherited) {
        return switch (inherited.size()) {
            case 0 -> null;
            case 1 -> inherited.get(0);
            default -> new ComposedAgentListener(inherited);
        };
    }
}
