package dev.langchain4j.model.huggingface.spi;

import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link HuggingFaceEmbeddingModel.HuggingFaceEmbeddingModelBuilder} instances.
 */
public interface HuggingFaceEmbeddingModelBuilderFactory extends Supplier<HuggingFaceEmbeddingModel.HuggingFaceEmbeddingModelBuilder> {
}
