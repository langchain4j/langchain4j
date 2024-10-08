package dev.langchain4j.model.jlama.spi;

import dev.langchain4j.model.jlama.JlamaEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link JlamaEmbeddingModel.JlamaEmbeddingModelBuilder} instances.
 */
public interface JlamaEmbeddingModelBuilderFactory extends Supplier<JlamaEmbeddingModel.JlamaEmbeddingModelBuilder> {
}
