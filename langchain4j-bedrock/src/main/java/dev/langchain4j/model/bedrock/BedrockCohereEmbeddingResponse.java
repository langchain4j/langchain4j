package dev.langchain4j.model.bedrock;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.bedrock.internal.BedrockEmbeddingResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Bedrock Cohere embedding response
 */
@Getter
@Setter
public class BedrockCohereEmbeddingResponse implements BedrockEmbeddingResponse {

    private String id;

    private List<String> texts;

    private float[] embedding;

    private int inputTextTokenCount;

    @Override
    public Embedding toEmbedding() {
        return new Embedding(embedding);
    }

}
