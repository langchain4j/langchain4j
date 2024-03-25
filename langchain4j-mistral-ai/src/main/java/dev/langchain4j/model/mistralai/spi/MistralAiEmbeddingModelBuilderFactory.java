package dev.langchain4j.model.mistralai.spi;

import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link dev.langchain4j.model.mistralai.MistralAiEmbeddingModel.MistralAiEmbeddingModelBuilder} instances.
 */
public interface MistralAiEmbeddingModelBuilderFactory extends Supplier<MistralAiEmbeddingModel.MistralAiEmbeddingModelBuilder> {
}
