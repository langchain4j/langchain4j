package dev.langchain4j.model.jitllm;

import java.nio.file.Files;
import java.nio.file.Path;

final class TestModelPath {

    private TestModelPath() {}

    static Path fromEnvironment() {
        Path modelPath = Path.of(System.getenv("MODEL"));
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalStateException("MODEL does not point to a GGUF file: " + modelPath);
        }
        return modelPath;
    }
}
