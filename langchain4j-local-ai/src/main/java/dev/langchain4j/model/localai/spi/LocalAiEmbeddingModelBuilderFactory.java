package dev.langchain4j.model.localai.spi;

import dev.langchain4j.model.localai.LocalAiEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link LocalAiEmbeddingModel.LocalAiEmbeddingModelBuilder} instances.
 */
public interface LocalAiEmbeddingModelBuilderFactory extends Supplier<LocalAiEmbeddingModel.LocalAiEmbeddingModelBuilder> {
}
