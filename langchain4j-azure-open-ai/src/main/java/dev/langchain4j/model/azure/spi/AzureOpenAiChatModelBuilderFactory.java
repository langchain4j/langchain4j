package dev.langchain4j.model.azure.spi;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link AzureOpenAiChatModel.Builder} instances.
 */
public interface AzureOpenAiChatModelBuilderFactory extends Supplier<AzureOpenAiChatModel.Builder> {
}
