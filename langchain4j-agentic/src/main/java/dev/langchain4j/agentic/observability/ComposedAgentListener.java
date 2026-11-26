package dev.langchain4j.agentic.observability;

import dev.langchain4j.Internal;
import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Composes several {@link AgentListener}s into one, notifying them in a defined order: the callbacks that open a scope
 * ({@code beforeAgentInvocation}, {@code beforeAgentToolExecution}, {@code afterAgenticScopeCreated}) and the events
 * notified within it notify the listeners in the order they were registered, inherited listeners first, while the
 * callbacks that close a scope ({@code afterAgentInvocation}, {@code onAgentInvocationError},
 * {@code afterAgentToolExecution}, {@code beforeAgenticScopeDestroyed}, {@code onAgenticSystemSuspended}) notify them in
 * the reverse order. This way the listeners nest like interceptors: a listener that makes a context current in
 * {@code beforeAgentInvocation} and restores the previous one in {@code afterAgentInvocation} gives every listener
 * registered after it that same context in both callbacks.
 */
@Internal
public class ComposedAgentListener implements AgentListener {

    private final List<AgentListener> listeners;

    private ComposedAgentListener(List<AgentListener> listeners) {
        this.listeners = listeners.stream().distinct().collect(Collectors.toCollection(ArrayList::new));
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
            for (AgentListener composedListener : composed.listeners) {
                addIfAbsent(composedListener);
            }
        } else {
            addIfAbsent(listener);
        }
    }

    private void addIfAbsent(AgentListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
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
        notifyEachInReverse(listener -> listener.afterAgentInvocation(agentResponse));
    }

    @Override
    public void onAgentInvocationError(final AgentInvocationError agentInvocationError) {
        notifyEachInReverse(listener -> listener.onAgentInvocationError(agentInvocationError));
    }

    @Override
    public void onAgentSkipped(final AgentSkipped agentSkipped) {
        notifyEach(listener -> listener.onAgentSkipped(agentSkipped));
    }

    @Override
    public void afterAgentToolExecution(AfterAgentToolExecution afterAgentToolExecution) {
        notifyEachInReverse(listener -> listener.afterAgentToolExecution(afterAgentToolExecution));
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
    public void onPlannerModelResponse(PlannerModelResponse plannerModelResponse) {
        notifyEach(listener -> listener.onPlannerModelResponse(plannerModelResponse));
    }

    @Override
    public void afterAgenticScopeCreated(final AgenticScope agenticScope) {
        notifyEach(listener -> listener.afterAgenticScopeCreated(agenticScope));
    }

    @Override
    public void beforeAgenticScopeDestroyed(final AgenticScope agenticScope) {
        notifyEachInReverse(listener -> listener.beforeAgenticScopeDestroyed(agenticScope));
    }

    @Override
    public void onAgenticSystemSuspended(final AgenticScope agenticScope) {
        notifyEachInReverse(listener -> listener.onAgenticSystemSuspended(agenticScope));
    }

    private void notifyEach(Consumer<AgentListener> notification) {
        notifyEach(listeners.iterator(), notification);
    }

    private void notifyEachInReverse(Consumer<AgentListener> notification) {
        notifyEach(reverseIterator(), notification);
    }

    private Iterator<AgentListener> reverseIterator() {
        ListIterator<AgentListener> iterator = listeners.listIterator(listeners.size());
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasPrevious();
            }

            @Override
            public AgentListener next() {
                return iterator.previous();
            }
        };
    }

    /**
     * Notifies every given listener, in order, even when one of them throws: a failing listener must not keep the
     * others, which may depend on the notification (e.g. to propagate a context), from being notified. The first
     * failure is rethrown once all the listeners have been notified, with any further ones added to it as suppressed
     * exceptions.
     */
    private static void notifyEach(Iterator<AgentListener> listeners, Consumer<AgentListener> notification) {
        RuntimeException failure = null;
        while (listeners.hasNext()) {
            AgentListener listener = listeners.next();
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
