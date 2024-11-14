package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.data.embedding.Embedding;

import java.util.List;

/**
 * Bedrock embedding response
 */
public interface BedrockEmbeddingResponse {

    /**
     * Get embeddings.
     * Some models allow multiple TextSegments and as result return multiple embeddings.
     * In this case, we need to return a list of embeddings.
     *
     * @return list of embedding
     */
    List<Embedding> toEmbeddings();

    /**
     * Get input text token count
     *
     * @return input text token count
     */
    int getInputTextTokenCount();

}
