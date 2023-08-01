package dev.langchain4j.model.embedding;

import java.io.InputStream;

public class E5_SMALL_V2_Q_EmbeddingModel extends AbstractInProcessEmbeddingModel {

    private static volatile OnnxBertEmbeddingModel MODEL;

    public E5_SMALL_V2_Q_EmbeddingModel() {
        if (MODEL == null) {
            synchronized (E5_SMALL_V2_Q_EmbeddingModel.class) {
                if (MODEL == null) {
                    InputStream modelInputStream = getClass().getResourceAsStream("/e5-small-v2-q.onnx");
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
