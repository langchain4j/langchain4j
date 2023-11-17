package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.bedrock.internal.BedrockEmbeddingResponse;
import lombok.Getter;
import lombok.Setter;

/**
 * Bedrock Titan embedding response
 */
@Getter
@Setter
public class BedrockTitanEmbeddingResponse implements BedrockEmbeddingResponse {

    private float[] embedding;
    private int inputTextTokenCount;

    @Override
    public Embedding toEmbedding() {
        return new Embedding(embedding);
    }
}
