package dev.langchain4j.model.azure.spi;

import dev.langchain4j.model.azure.AzureOpenAiLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link AzureOpenAiLanguageModel.Builder} instances.
 */
public interface AzureOpenAiLanguageModelBuilderFactory extends Supplier<AzureOpenAiLanguageModel.Builder> {
}
