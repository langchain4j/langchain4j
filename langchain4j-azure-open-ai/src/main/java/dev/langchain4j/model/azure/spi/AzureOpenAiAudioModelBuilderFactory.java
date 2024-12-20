package dev.langchain4j.model.azure.spi;

import dev.langchain4j.model.azure.AzureOpenAiAudioModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link AzureOpenAiAudioModel.Builder} instances.
 */
public interface AzureOpenAiAudioModelBuilderFactory extends Supplier<AzureOpenAiAudioModel.Builder> {
    
}
