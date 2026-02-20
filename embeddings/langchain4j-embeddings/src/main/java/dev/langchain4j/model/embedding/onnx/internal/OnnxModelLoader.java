package dev.langchain4j.model.embedding.onnx.internal;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import dev.langchain4j.Internal;
import java.nio.file.Path;

/**
 * Loads an ONNX model into an ORT session from a file path or InputStream.
 */
@Internal
public class OnnxModelLoader implements AutoCloseable {
    private final OrtEnvironment environment;
    private final OrtSession session;

    public OnnxModelLoader(Path pathToModel) {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(pathToModel.toString());
        } catch (OrtException e) {
            throw new OnnxModelLoadingException(e);
        }
    }

    public OrtEnvironment environment() {
        return environment;
    }

    public OrtSession session() {
        return session;
    }

    @Override
    public void close() throws Exception {
        session.close();
    }

    static class OnnxModelLoadingException extends RuntimeException {
        OnnxModelLoadingException(Exception e) {
            super(e);
        }
    }
}
