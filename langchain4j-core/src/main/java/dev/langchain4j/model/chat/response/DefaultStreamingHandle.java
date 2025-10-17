package dev.langchain4j.model.chat.response;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.io.IOException;
import java.io.InputStream;

public class DefaultStreamingHandle implements StreamingHandle {

    private final InputStream inputStream;
    private volatile boolean isCancelled;

    public DefaultStreamingHandle(InputStream inputStream) {
        this.inputStream = ensureNotNull(inputStream, "inputStream");
    }

    @Override
    public void cancel() {
        isCancelled = true; // TODO?
        try {
            inputStream.close();
        } catch (IOException ignored) { // TODO?
        }
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }
}
