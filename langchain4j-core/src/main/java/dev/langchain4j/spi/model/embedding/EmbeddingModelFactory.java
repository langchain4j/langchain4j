package dev.langchain4j.spi.model.embedding;

import dev.langchain4j.Internal;
import dev.langchain4j.model.embedding.EmbeddingModel;

/**
 * A factory for creating {@link EmbeddingModel} instances through SPI.
 * <br>
 * For the "Easy RAG", import {@code langchain4j-easy-rag} module,
 * which contains a {@code EmbeddingModelFactory} implementation.
 */
@Internal
public interface EmbeddingModelFactory {

    EmbeddingModel create();
}
