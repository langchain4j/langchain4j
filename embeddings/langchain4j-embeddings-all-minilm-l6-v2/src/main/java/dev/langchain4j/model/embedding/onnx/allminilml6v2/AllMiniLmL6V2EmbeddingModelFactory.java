package dev.langchain4j.model.embedding.onnx.allminilml6v2;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;

public class AllMiniLmL6V2EmbeddingModelFactory implements EmbeddingModelFactory {

    @Override
    public EmbeddingModel create() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
