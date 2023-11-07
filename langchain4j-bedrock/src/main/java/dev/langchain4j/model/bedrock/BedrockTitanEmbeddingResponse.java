package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.bedrock.internal.BedrockEmbeddingInstance;
import lombok.Getter;
import lombok.Setter;

/**
 * Bedrock Titan embedding response
 */
@Getter
@Setter
public class BedrockTitanEmbeddingResponse implements BedrockEmbeddingInstance {

    private float[] embedding;
    private int inputTextTokenCount;

    @Override
    public Embedding toEmbedding() {
        return new Embedding(embedding);
    }
}
