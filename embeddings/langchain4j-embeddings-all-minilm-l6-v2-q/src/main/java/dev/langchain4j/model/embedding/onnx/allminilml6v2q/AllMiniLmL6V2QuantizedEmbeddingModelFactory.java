package dev.langchain4j.model.embedding.onnx.allminilml6v2q;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;

public class AllMiniLmL6V2QuantizedEmbeddingModelFactory implements EmbeddingModelFactory {

    @Override
    public EmbeddingModel create() {
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
    }
}
