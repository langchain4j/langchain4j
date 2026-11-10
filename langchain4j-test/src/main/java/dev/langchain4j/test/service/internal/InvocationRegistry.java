package dev.langchain4j.test.service.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assigns AI Service events to recordings. An invocation belongs to a recording when it starts on a thread that is
 * recording, or when it was started by an invocation that belongs to it. All later events of the invocation are
 * matched by its invocation id.
 */
@Internal
public final class InvocationRegistry {

    private static final ThreadLocal<RecordingSession> CURRENT = new ThreadLocal<>();
    private static final Map<UUID, InvocationRecord> TRACKED = new ConcurrentHashMap<>();

    private static volatile boolean listening;

    private InvocationRegistry() {}

    static void listening() {
        listening = true;
    }

    /**
     * Returns whether the recording listeners were registered with at least one AI Service in this JVM.
     */
    public static boolean isListening() {
        return listening;
    }

    /**
     * Starts a recording on the current thread.
     *
     * @return the recording that was active on this thread before, to be passed to {@link #stopRecordingThread}
     */
    public static RecordingSession startRecordingThread(RecordingSession recording) {
        RecordingSession previous = CURRENT.get();
        CURRENT.set(recording);
        return previous;
    }

    public static void stopRecordingThread(RecordingSession previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    public static RecordingSession newSession() {
        return new RecordingSession();
    }

    /**
     * Stops matching events to the invocations of the given recording.
     */
    public static void close(RecordingSession recording) {
        recording.close();
        for (InvocationRecord invocation : recording.invocations()) {
            TRACKED.remove(invocation.invocationContext().invocationId(), invocation);
        }
    }

    /**
     * Records a model call made for a captured invocation.
     *
     * @return the recorded call, or {@code null} if the call was not made for a captured invocation
     */
    static ModelCallRecord onModelCall(UUID invocationId, ModelProvider modelProvider, Object request) {
        if (invocationId == null) {
            return null;
        }
        InvocationRecord invocation = TRACKED.get(invocationId);
        return invocation == null ? null : invocation.addModelCall(modelProvider, request);
    }

    static void onEvent(AiServiceEvent event) {
        InvocationContext invocationContext = event.invocationContext();
        if (invocationContext == null || invocationContext.invocationId() == null) {
            return;
        }
        if (event instanceof AiServiceStartedEvent) {
            onStarted(event, invocationContext);
            return;
        }
        InvocationRecord invocation = TRACKED.get(invocationContext.invocationId());
        if (invocation != null) {
            invocation.add(event);
        }
    }

    private static void onStarted(AiServiceEvent event, InvocationContext invocationContext) {
        InvocationRecord parent = invocationContext.parentInvocationId() == null
                ? null
                : TRACKED.get(invocationContext.parentInvocationId());
        RecordingSession recording = parent != null ? parent.recording() : CURRENT.get();
        if (recording == null) {
            return;
        }
        InvocationRecord invocation = new InvocationRecord(recording, invocationContext, parent);
        invocation.add(event);
        if (TRACKED.putIfAbsent(invocationContext.invocationId(), invocation) != null) {
            return;
        }
        if (!recording.add(invocation)) {
            TRACKED.remove(invocationContext.invocationId(), invocation);
        }
    }
}
