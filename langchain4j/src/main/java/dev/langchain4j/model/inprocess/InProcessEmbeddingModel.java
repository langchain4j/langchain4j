package dev.langchain4j.model.inprocess;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.lang.String.format;

/**
 * An embedding model that runs within your Java application's process.
 * Several pre-packaged embedding models are available out-of-the-box, see {@link InProcessEmbeddingModelType}.
 * Additionally, any BERT-based model (e.g., from HuggingFace) can be used, as long as it is in ONNX format.
 * Information on how to convert models into ONNX format can be found <a href="https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model">here</a>.
 * Many models converted to ONNX format are available <a href="https://huggingface.co/Xenova">here</a>.
 * This class is a thin wrapper over the actual implementations.
 * Specific implementation classes, along with the models, are located in separate,
 * optional langchain4j-embeddings-xxx modules and are loaded dynamically.
 * If you wish to use one of the pre-packaged models (e.g., e5-small-v2),
 * please add the corresponding dependency (e.g., langchain4j-embeddings-e5-small-v2) to your project.
 * If you wish to use a custom model, please add the langchain4j-embeddings dependency to your project.
 * The model is executed using the ONNX Runtime.
 */
public class InProcessEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel implementation;

    /**
     * Loads one of the pre-packaged embedding models.
     * Requires "langchain4j-embeddings-${type}" dependency.
     *
     * @param type The model type to load.
     */
    public InProcessEmbeddingModel(InProcessEmbeddingModelType type) {
        try {
            implementation = loadPrePackagedModelDynamically(type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(errorMessageForPrePackagedModel(type), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a custom embedding model.
     * Requires "langchain4j-embeddings" dependency.
     *
     * @param pathToModel The path to the .onnx model file (e.g., "/home/me/model.onnx").
     */
    public InProcessEmbeddingModel(String pathToModel) {
        this(Paths.get(pathToModel));
    }

    /**
     * Loads a custom embedding model.
     * Requires "langchain4j-embeddings" dependency.
     *
     * @param pathToModel The path to the .onnx model file (e.g., "/home/me/model.onnx").
     */
    public InProcessEmbeddingModel(Path pathToModel) {
        try {
            implementation = loadCustomModelDynamically(pathToModel);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(errorMessageForCustomModel(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static EmbeddingModel loadPrePackagedModelDynamically(InProcessEmbeddingModelType type) throws Exception {
        Class<?> implementationClass = Class.forName(
                format("dev.langchain4j.model.embedding.%s_EmbeddingModel", type.name()));
        return (EmbeddingModel) implementationClass.getConstructor().newInstance();
    }

    private static EmbeddingModel loadCustomModelDynamically(Path pathToModel) throws Exception {
        Class<?> implementationClass = Class.forName(
                "dev.langchain4j.model.embedding.CustomInProcessEmbeddingModel");
        Class<?>[] constructorParameterTypes = new Class<?>[]{Path.class};
        Constructor<?> constructor = implementationClass.getConstructor(constructorParameterTypes);
        return (EmbeddingModel) constructor.newInstance(pathToModel);
    }

    private static String errorMessageForPrePackagedModel(InProcessEmbeddingModelType type) {
        return format("To use %s embedding model, please add the following dependency to your project:\n"
                        + "\n"
                        + "Maven:\n"
                        + "<dependency>\n" +
                        "    <groupId>dev.langchain4j</groupId>\n" +
                        "    <artifactId>langchain4j-embeddings-%s</artifactId>\n" +
                        "    <version>0.22.0</version>\n" +
                        "</dependency>\n"
                        + "\n"
                        + "Gradle:\n"
                        + "implementation 'dev.langchain4j:langchain4j-embeddings-%s:0.22.0'\n",
                type.name(),
                type.name().replace("_", "-").toLowerCase(),
                type.name().replace("_", "-").toLowerCase()
        );
    }

    private static String errorMessageForCustomModel() {
        return "To use custom embedding model, please add the following dependency to your project:\n"
                + "\n"
                + "Maven:\n"
                + "<dependency>\n" +
                "    <groupId>dev.langchain4j</groupId>\n" +
                "    <artifactId>langchain4j-embeddings</artifactId>\n" +
                "    <version>0.22.0</version>\n" +
                "</dependency>\n"
                + "\n"
                + "Gradle:\n"
                + "implementation 'dev.langchain4j:langchain4j-embeddings:0.22.0'\n";
    }

    @Override
    public List<Embedding> embedAll(List<TextSegment> textSegments) {
        return implementation.embedAll(textSegments);
    }
}
