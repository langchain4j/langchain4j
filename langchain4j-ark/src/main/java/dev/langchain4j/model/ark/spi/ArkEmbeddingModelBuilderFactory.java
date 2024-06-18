package dev.langchain4j.model.ark.spi;

import dev.langchain4j.model.ark.ArkEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link ArkEmbeddingModel.ArkEmbeddingModelBuilder} instances.
 */
public interface ArkEmbeddingModelBuilderFactory extends Supplier<ArkEmbeddingModel.ArkEmbeddingModelBuilder> {
}
