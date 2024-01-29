package dev.langchain4j.model.azure.spi;

import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link AzureOpenAiEmbeddingModel.Builder} instances.
 */
public interface AzureOpenAiEmbeddingModelBuilderFactory extends Supplier<AzureOpenAiEmbeddingModel.Builder> {
}
