package dev.langchain4j.model.azure.spi;

import dev.langchain4j.model.azure.AzureOpenAiStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link AzureOpenAiStreamingChatModel.Builder} instances.
 */
public interface AzureOpenAiStreamingChatModelBuilderFactory extends Supplier<AzureOpenAiStreamingChatModel.Builder> {
}
