package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.data.embedding.Embedding;

/**
 * Bedrock embedding instance
 */
public interface BedrockEmbeddingInstance {

    /**
     * Get embedding
     *
     * @return embedding
     */
    Embedding toEmbedding();

    /**
     * Get input text token count
     *
     * @return input text token count
     */
    int getInputTextTokenCount();

}
