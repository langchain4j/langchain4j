package dev.langchain4j.model.embedding;

public class e5_small_v2_q_EmbeddingModel extends AbstractInProcessEmbeddingModel {

    private static final OnnxEmbeddingModel model = new OnnxEmbeddingModel(
            "/e5-small-v2-q.onnx",
            "/vocab.txt"
    );

    @Override
    protected OnnxEmbeddingModel model() {
        return model;
    }
}
