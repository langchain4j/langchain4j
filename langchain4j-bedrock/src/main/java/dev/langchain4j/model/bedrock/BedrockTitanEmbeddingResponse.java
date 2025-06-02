package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.bedrock.internal.BedrockEmbeddingResponse;

/**
 * Bedrock Titan embedding response
 */
public class BedrockTitanEmbeddingResponse implements BedrockEmbeddingResponse {

    private float[] embedding;
    private int inputTextTokenCount;

    @Override
    public Embedding toEmbedding() {
        return new Embedding(embedding);
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(final float[] embedding) {
        this.embedding = embedding;
    }

    public int getInputTextTokenCount() {
        return inputTextTokenCount;
    }

    public void setInputTextTokenCount(final int inputTextTokenCount) {
        this.inputTextTokenCount = inputTextTokenCount;
    }
}
