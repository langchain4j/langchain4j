package dev.langchain4j.model.embedding;

public class e5_small_v2_EmbeddingModel extends AbstractInProcessEmbeddingModel {

    private static final OnnxEmbeddingModel model = new OnnxEmbeddingModel(
            "/e5-small-v2.onnx",
            "/vocab.txt"
    );

    @Override
    protected OnnxEmbeddingModel model() {
        return model;
    }
}
