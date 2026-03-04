package dev.langchain4j.agentic.observability;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Internal
public class ComposedAgentListener implements AgentListener {

    private final Set<AgentListener> listeners;

    private ComposedAgentListener(List<AgentListener> listeners) {
        this.listeners = new HashSet<>(listeners);
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

    public Collection<AgentListener> listeners() {
        return listeners;
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
    public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
        for (AgentListener listener : listeners) {
            listener.afterAgentToolExecution(afterAgentToolExecution);
        }
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) {
        for (AgentListener listener : listeners) {
            listener.beforeAgentToolExecution(beforeAgentToolExecution);
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

    public boolean contains(AgentListener listener) {
        if (listener instanceof ComposedAgentListener composed) {
            return listeners.containsAll(composed.listeners);
        }
        return listeners.contains(listener);
    }

    @Override
    public boolean inheritedBySubagents() {
        return listeners.stream().anyMatch(AgentListener::inheritedBySubagents);
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

    public static <T extends AgentListener> T listenerOfType(AgentListener rootListener, Class<T> listenerType) {
        if (listenerType.isInstance(rootListener)) {
            return (T) rootListener;
        }
        if (rootListener instanceof ComposedAgentListener composed) {
            for (AgentListener listener : composed.listeners) {
                if (listenerType.isInstance(listener)) {
                    return (T) listener;
                }
            }
        }
        return null;
    }
}
