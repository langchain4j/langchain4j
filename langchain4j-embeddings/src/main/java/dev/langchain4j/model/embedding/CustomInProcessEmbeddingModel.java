package dev.langchain4j.model.embedding;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CustomInProcessEmbeddingModel extends AbstractInProcessEmbeddingModel {

    private final OnnxBertEmbeddingModel model;

    public CustomInProcessEmbeddingModel(Path pathToModel) {
        try {
            InputStream inputStream = Files.newInputStream(pathToModel);
            model = new OnnxBertEmbeddingModel(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected OnnxBertEmbeddingModel model() {
        return model;
    }
}
