package dev.langchain4j.test.service.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.model.ModelProvider;

/**
 * A model call made for a recorded AI Service invocation. Its response or error is set when the call ends.
 */
@Internal
public final class ModelCallRecord {

    private final RecordingSession recording;
    private final ModelProvider modelProvider;
    private final Object request;
    private Object response;
    private Throwable error;
    private boolean ended;

    ModelCallRecord(RecordingSession recording, ModelProvider modelProvider, Object request) {
        this.recording = recording;
        this.modelProvider = modelProvider;
        this.request = request;
    }

    void end(Object response, Throwable error) {
        synchronized (recording) {
            if (ended) {
                return;
            }
            this.response = response;
            this.error = error;
            this.ended = true;
        }
    }

    public ModelProvider modelProvider() {
        return modelProvider;
    }

    public Object request() {
        return request;
    }

    public Object response() {
        synchronized (recording) {
            return response;
        }
    }

    public Throwable error() {
        synchronized (recording) {
            return error;
        }
    }
}
