package dev.langchain4j.model.embedding.onnx.e5smallv2q;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;

public class E5SmallV2QuantizedEmbeddingModelFactory implements EmbeddingModelFactory {

    @Override
    public EmbeddingModel create() {
        return new E5SmallV2QuantizedEmbeddingModel();
    }
}
