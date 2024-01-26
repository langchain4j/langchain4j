package dev.langchain4j.model.azure.spi;

import dev.langchain4j.model.azure.AzureOpenAiImageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link AzureOpenAiImageModel.Builder} instances.
 */
public interface AzureOpenAiImageModelBuilderFactory extends Supplier<AzureOpenAiImageModel.Builder> {
}
