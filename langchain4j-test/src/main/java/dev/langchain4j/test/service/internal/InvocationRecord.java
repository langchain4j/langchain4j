package dev.langchain4j.test.service.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The events and model calls of one recorded AI Service invocation. Events that arrive after the first completed, error or
 * cancelled event are ignored, and so is an event received a second time.
 */
@Internal
public final class InvocationRecord {

    private final RecordingSession recording;
    private final InvocationContext invocationContext;
    private final InvocationRecord parent;
    private final List<AiServiceEvent> events = new ArrayList<>();
    private final List<InvocationRecord> children = new ArrayList<>();
    private final List<ModelCallRecord> modelCalls = new ArrayList<>();

    InvocationRecord(RecordingSession recording, InvocationContext invocationContext, InvocationRecord parent) {
        this.recording = recording;
        this.invocationContext = invocationContext;
        this.parent = parent;
    }

    RecordingSession recording() {
        return recording;
    }

    public InvocationContext invocationContext() {
        return invocationContext;
    }

    public InvocationRecord parent() {
        return parent;
    }

    public List<AiServiceEvent> events() {
        synchronized (recording) {
            return List.copyOf(events);
        }
    }

    public List<InvocationRecord> children() {
        synchronized (recording) {
            return List.copyOf(children);
        }
    }

    void add(AiServiceEvent event) {
        synchronized (recording) {
            if (isFinished() || events.stream().anyMatch(recorded -> recorded == event)) {
                return;
            }
            events.add(event);
            recording.notifyAll();
        }
    }

    /**
     * Returns the model calls made for this invocation, in the order they started.
     */
    public List<ModelCallRecord> modelCalls() {
        synchronized (recording) {
            return List.copyOf(modelCalls);
        }
    }

    ModelCallRecord addModelCall(ModelProvider modelProvider, Object request) {
        synchronized (recording) {
            if (isFinished()) {
                return null;
            }
            ModelCallRecord call = new ModelCallRecord(recording, modelProvider, request);
            modelCalls.add(call);
            return call;
        }
    }

    void addChild(InvocationRecord child) {
        synchronized (recording) {
            children.add(child);
        }
    }

    public boolean isFinished() {
        synchronized (recording) {
            return events.stream().anyMatch(InvocationRecord::isTerminal);
        }
    }

    public boolean isCancelled() {
        synchronized (recording) {
            return events.stream().anyMatch(event -> event instanceof AiServiceCancelledEvent);
        }
    }

    public boolean hasIssuedRequest() {
        synchronized (recording) {
            return events.stream().anyMatch(event -> event instanceof AiServiceRequestIssuedEvent);
        }
    }

    public AiServiceEvent lastEvent() {
        synchronized (recording) {
            return events.get(events.size() - 1);
        }
    }

    private static boolean isTerminal(AiServiceEvent event) {
        return event instanceof AiServiceCompletedEvent
                || event instanceof AiServiceErrorEvent
                || event instanceof AiServiceCancelledEvent;
    }
}
