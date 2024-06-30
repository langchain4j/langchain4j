package dev.langchain4j.model.jlama.spi;

import java.util.function.Supplier;

import dev.langchain4j.model.jlama.JlamaEmbeddingModel;

/**
 * A factory for building {@link JlamaEmbeddingModel.JlamaEmbeddingModelBuilder} instances.
 */
public interface JlamaEmbeddingModelBuilderFactory extends Supplier<JlamaEmbeddingModel.JlamaEmbeddingModelBuilder>
{
}
