package dev.langchain4j.model.ollama.spi;

import dev.langchain4j.model.ollama.OllamaEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OllamaEmbeddingModel.OllamaEmbeddingModelBuilder} instances.
 */
public interface OllamaEmbeddingModelBuilderFactory extends Supplier<OllamaEmbeddingModel.OllamaEmbeddingModelBuilder> {
}
