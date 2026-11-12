package dev.langchain4j.agentic.observability;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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
        notifyEach(listener -> listener.beforeAgentInvocation(agentRequest));
    }

    @Override
    public void afterAgentInvocation(final AgentResponse agentResponse) {
        notifyEach(listener -> listener.afterAgentInvocation(agentResponse));
    }

    @Override
    public void onAgentInvocationError(final AgentInvocationError agentInvocationError) {
        notifyEach(listener -> listener.onAgentInvocationError(agentInvocationError));
    }

    @Override
    public void onAgentSkipped(final AgentSkipped agentSkipped) {
        notifyEach(listener -> listener.onAgentSkipped(agentSkipped));
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
        notifyEach(listener -> listener.afterAgentToolExecution(afterAgentToolExecution));
    }

    @Override
    public void beforeAgentToolExecution(BeforeAgentToolExecution beforeAgentToolExecution) {
        notifyEach(listener -> listener.beforeAgentToolExecution(beforeAgentToolExecution));
    }

    @Override
    public void onAgentAiServiceEvent(AgentAiServiceEvent agentAiServiceEvent) {
        notifyEach(listener -> listener.onAgentAiServiceEvent(agentAiServiceEvent));
    }

    @Override
    public void afterAgenticScopeCreated(final AgenticScope agenticScope) {
        notifyEach(listener -> listener.afterAgenticScopeCreated(agenticScope));
    }

    @Override
    public void beforeAgenticScopeDestroyed(final AgenticScope agenticScope) {
        notifyEach(listener -> listener.beforeAgenticScopeDestroyed(agenticScope));
    }

    @Override
    public void onAgenticSystemSuspended(final AgenticScope agenticScope) {
        notifyEach(listener -> listener.onAgenticSystemSuspended(agenticScope));
    }

    /**
     * Notifies every composed listener, even when one of them throws: a failing listener must not keep the others,
     * which may depend on the notification (e.g. to propagate a context), from being notified. Since the listeners are
     * kept in a {@link HashSet}, their order is not deterministic, so without this a listener that throws would skip a
     * different set of listeners from one run to the next. The first failure is rethrown once all the listeners have
     * been notified, with any further ones added to it as suppressed exceptions.
     */
    private void notifyEach(Consumer<AgentListener> notification) {
        RuntimeException failure = null;
        for (AgentListener listener : listeners) {
            try {
                notification.accept(listener);
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
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

    /**
     * Composes a newly registered listener with the existing one, if any. When the existing listener is already a
     * {@link ComposedAgentListener}, the new listener is added to it.
     *
     * @return the listener to be used in place of the existing one
     */
    public static AgentListener compose(AgentListener existingListener, AgentListener newListener) {
        if (existingListener == null) {
            return newListener;
        }
        if (existingListener instanceof ComposedAgentListener composed) {
            composed.addListener(newListener);
            return composed;
        }
        return new ComposedAgentListener(existingListener, newListener);
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

    private static void add(
            List<AgentListener> existingListeners, AgentListener newListener, Predicate<AgentListener> filter) {
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
