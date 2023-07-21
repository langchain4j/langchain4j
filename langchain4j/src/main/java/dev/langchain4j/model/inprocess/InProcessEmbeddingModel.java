package dev.langchain4j.model.inprocess;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

import static java.lang.String.format;

public class InProcessEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel implementation;

    public InProcessEmbeddingModel(InProcessEmbeddingModelType type) {
        try {
            implementation = loadDynamically(type);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(getMessage(type), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static EmbeddingModel loadDynamically(InProcessEmbeddingModelType type) throws Exception {
        Class<?> implementationClass = Class.forName(
                format("dev.langchain4j.model.embedding.%s_EmbeddingModel", type.name().toLowerCase()));
        return (EmbeddingModel) implementationClass.getConstructor().newInstance();
    }

    private static String getMessage(InProcessEmbeddingModelType type) {
        return format("To use %s embedding model, please add the following dependency to your project:\n"
                        + "\n"
                        + "Maven:\n"
                        + "<dependency>\n" +
                        "    <groupId>dev.langchain4j</groupId>\n" +
                        "    <artifactId>langchain4j-embeddings-%s</artifactId>\n" +
                        "    <version>0.16.0</version>\n" +
                        "</dependency>\n"
                        + "\n"
                        + "Gradle:\n"
                        + "implementation 'dev.langchain4j:langchain4j-embeddings-%s:0.16.0'\n",
                type.name(),
                type.name().replace("_", "-").toLowerCase(),
                type.name().replace("_", "-").toLowerCase()
        );
    }

    @Override
    public List<Embedding> embedAll(List<TextSegment> textSegments) {
        return implementation.embedAll(textSegments);
    }
}
