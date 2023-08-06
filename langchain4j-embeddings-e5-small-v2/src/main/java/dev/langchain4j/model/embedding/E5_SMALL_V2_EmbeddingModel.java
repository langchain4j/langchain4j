package dev.langchain4j.model.embedding;

public class E5_SMALL_V2_EmbeddingModel extends AbstractInProcessEmbeddingModel {

    private static final OnnxBertEmbeddingModel MODEL = load("/e5-small-v2.onnx");

    @Override
    protected OnnxBertEmbeddingModel model() {
        return MODEL;
    }
}
