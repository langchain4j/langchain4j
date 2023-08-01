package dev.langchain4j.model.embedding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CustomInProcessEmbeddingModel extends AbstractInProcessEmbeddingModel {

    private static volatile OnnxBertEmbeddingModel MODEL;

    public CustomInProcessEmbeddingModel(Path pathToModel) {
        if (MODEL == null) {
            synchronized (CustomInProcessEmbeddingModel.class) {
                if (MODEL == null) {
                    try {
                        InputStream modelInputStream = Files.newInputStream(pathToModel);
                        MODEL = new OnnxBertEmbeddingModel(modelInputStream);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    protected OnnxBertEmbeddingModel model() {
        return MODEL;
    }
}
