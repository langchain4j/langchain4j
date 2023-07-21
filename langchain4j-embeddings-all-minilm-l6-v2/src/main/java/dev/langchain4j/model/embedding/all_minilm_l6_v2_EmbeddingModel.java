package dev.langchain4j.model.embedding;

public class all_minilm_l6_v2_EmbeddingModel extends AbstractInProcessEmbeddingModel {

    private static final OnnxEmbeddingModel model = new OnnxEmbeddingModel(
            "/all-minilm-l6-v2.onnx",
            "/vocab.txt"
    );

    @Override
    protected OnnxEmbeddingModel model() {
        return model;
    }
}
