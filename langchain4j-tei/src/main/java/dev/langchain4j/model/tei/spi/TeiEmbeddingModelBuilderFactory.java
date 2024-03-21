package dev.langchain4j.model.tei.spi;

import dev.langchain4j.model.tei.TeiEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link TeiEmbeddingModel.TeiEmbeddingModelBuilder} instances.
 */
public interface TeiEmbeddingModelBuilderFactory extends Supplier<TeiEmbeddingModel.TeiEmbeddingModelBuilder> {
}
