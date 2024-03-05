package dev.langchain4j.model.azure.spi;

import dev.langchain4j.model.azure.AzureOpenAiStreamingLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link AzureOpenAiStreamingLanguageModel.Builder} instances.
 */
public interface AzureOpenAiStreamingLanguageModelBuilderFactory extends Supplier<AzureOpenAiStreamingLanguageModel.Builder> {
}
