package dev.langchain4j.model.embedding.onnx;

/**
 * Embedding vector together with the token count reported by an encoder.
 */
public final class EmbeddingAndTokenCount {

    /**
     * The embedding vector.
     */
    public final float[] embedding;

    /**
     * The token count reported by the encoder.
     */
    public final int tokenCount;

    /**
     * Creates an embedding result with token usage.
     *
     * @param embedding The embedding vector.
     * @param tokenCount The token count reported by the encoder.
     */
    public EmbeddingAndTokenCount(float[] embedding, int tokenCount) {
        this.embedding = embedding;
        this.tokenCount = tokenCount;
    }
}
