package dev.langchain4j.model.bedrock;

import dev.langchain4j.Internal;
import dev.langchain4j.data.embedding.Embedding;

@Internal
interface BedrockEmbeddingResponse {

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
