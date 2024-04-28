package dev.langchain4j.model.sensenova.spi;

import dev.langchain4j.model.sensenova.SenseNovaEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link SenseNovaEmbeddingModel.SenseNovaEmbeddingModelBuilder} instances.
 */
public interface SenseNovaEmbeddingModelBuilderFactory extends Supplier<SenseNovaEmbeddingModel.SenseNovaEmbeddingModelBuilder> {
}
