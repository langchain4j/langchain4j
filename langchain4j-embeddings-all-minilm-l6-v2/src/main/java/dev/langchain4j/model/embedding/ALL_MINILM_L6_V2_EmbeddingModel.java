package dev.langchain4j.model.embedding;

import java.io.InputStream;

public class ALL_MINILM_L6_V2_EmbeddingModel extends AbstractInProcessEmbeddingModel {

    private static volatile OnnxBertEmbeddingModel MODEL;

    public ALL_MINILM_L6_V2_EmbeddingModel() {
        if (MODEL == null) {
            synchronized (ALL_MINILM_L6_V2_EmbeddingModel.class) {
                if (MODEL == null) {
                    InputStream modelInputStream = getClass().getResourceAsStream("/all-minilm-l6-v2.onnx");
                    MODEL = new OnnxBertEmbeddingModel(modelInputStream);
                }
            }
        }
    }

    @Override
    protected OnnxBertEmbeddingModel model() {
        return MODEL;
    }
}
