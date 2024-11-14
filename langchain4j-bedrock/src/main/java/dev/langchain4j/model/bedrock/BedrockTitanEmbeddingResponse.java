package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.bedrock.internal.BedrockEmbeddingResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Bedrock Titan embedding response
 */
@Getter
@Setter
public class BedrockTitanEmbeddingResponse implements BedrockEmbeddingResponse {

    private float[] embedding;
    private int inputTextTokenCount;

    @Override
    public List<Embedding> toEmbeddings() {
        return List.of(Embedding.from(embedding));
    }

}
