package dev.langchain4j.model.embedding;

import java.nio.file.Path;

public class CustomInProcessEmbeddingModel extends AbstractInProcessEmbeddingModel {

    private final OnnxBertEmbeddingModel model;

    public CustomInProcessEmbeddingModel(Path pathToModel) {
        model = load(pathToModel);
    }

    @Override
    protected OnnxBertEmbeddingModel model() {
        return model;
    }
}
