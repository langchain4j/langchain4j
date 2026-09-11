package dev.langchain4j.model.embedding.onnx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Gives the integration tests access to the CLIP model that the build downloads into {@code target/test-classes}
 * (see the {@code download-maven-plugin} executions in this module's {@code pom.xml}).
 */
class ClipTestModels {

    private ClipTestModels() {}

    static Path visionModel() {
        return resolve("/clip/vision_model.onnx");
    }

    static Path textModel() {
        return resolve("/clip/text_model.onnx");
    }

    static Path tokenizer() {
        return resolve("/clip/tokenizer.json");
    }

    private static Path resolve(String resource) {
        try {
            Path path = Paths.get(ClipTestModels.class.getResource(resource).toURI());
            if (!Files.exists(path)) {
                throw new IllegalStateException(missing(resource));
            }
            return path;
        } catch (NullPointerException | java.net.URISyntaxException e) {
            throw new IllegalStateException(missing(resource), e);
        }
    }

    private static String missing(String resource) {
        return "The CLIP model file " + resource + " is missing. It is downloaded by the build, so make sure "
                + "this module was built without -DembeddingsSkipDownload before running the integration tests.";
    }
}
