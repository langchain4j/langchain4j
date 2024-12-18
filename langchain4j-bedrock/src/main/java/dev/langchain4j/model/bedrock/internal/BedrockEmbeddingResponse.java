package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.data.embedding.Embedding;

import java.util.List;
import java.util.Optional;

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
     * Some models allow to return input text token count.
     * Other models do not provide this information.
     * @return Optional containing the token count of the input text
     * or empty if the data is insufficient.
     */
    Optional<Integer> getInputTextTokenCount();

}
