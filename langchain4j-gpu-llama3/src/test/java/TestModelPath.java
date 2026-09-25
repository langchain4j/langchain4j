import java.nio.file.Files;
import java.nio.file.Path;

final class TestModelPath {

    private TestModelPath() {}

    static Path fromEnvironment() {
        String model = System.getenv("MODEL");
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("MODEL must point to a compatible GGUF model file");
        }

        Path modelPath = Path.of(model);
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalStateException("MODEL does not point to a readable file: " + modelPath);
        }
        return modelPath;
    }
}
