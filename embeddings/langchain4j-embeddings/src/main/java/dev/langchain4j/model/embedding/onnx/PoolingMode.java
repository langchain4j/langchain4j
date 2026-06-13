package dev.langchain4j.model.embedding.onnx;

/**
 * Pooling strategies for ONNX embedding models.
 */
public enum PoolingMode {
    CLS,
    MEAN,

    /**
     * Mean pooling over tokens selected by the attention mask.
     */
    MEAN_MASKED,

    /**
     * Pooling that uses the final non-padding token hidden state.
     */
    LAST_TOKEN
}
