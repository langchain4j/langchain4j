package dev.langchain4j.test.service.internal;

import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A listener that passes the AI Service events of one type to the {@link InvocationRegistry}. One per event type is
 * registered with every AI Service by the {@link CaptureListenersProvider}. It never throws: an error in a listener
 * must not fail the AI Service invocation.
 */
final class RecordingListener<T extends AiServiceEvent> implements AiServiceListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingListener.class);

    private final Class<T> eventClass;

    RecordingListener(Class<T> eventClass) {
        this.eventClass = eventClass;
    }

    @Override
    public Class<T> getEventClass() {
        return eventClass;
    }

    @Override
    public void onEvent(T event) {
        try {
            InvocationRegistry.onEvent(event);
        } catch (Throwable t) {
            LOG.warn("Failed to capture AI Service event {}", event.getClass().getName(), t);
        }
    }
}
