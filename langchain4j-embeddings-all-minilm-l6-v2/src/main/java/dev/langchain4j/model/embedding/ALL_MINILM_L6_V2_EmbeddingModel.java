package dev.langchain4j.model.embedding;

public class ALL_MINILM_L6_V2_EmbeddingModel extends AbstractInProcessEmbeddingModel {

    private static final OnnxBertEmbeddingModel model = new OnnxBertEmbeddingModel("/all-minilm-l6-v2.onnx");

    @Override
    protected OnnxBertEmbeddingModel model() {
        return model;
    }
}
