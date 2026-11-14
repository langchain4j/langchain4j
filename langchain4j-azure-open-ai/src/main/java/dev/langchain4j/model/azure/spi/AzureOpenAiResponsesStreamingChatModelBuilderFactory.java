package dev.langchain4j.model.azure.spi;

import dev.langchain4j.model.azure.AzureOpenAiResponsesStreamingChatModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link AzureOpenAiResponsesStreamingChatModel.Builder} instances.
 */
public interface AzureOpenAiResponsesStreamingChatModelBuilderFactory
        extends Supplier<AzureOpenAiResponsesStreamingChatModel.Builder> {}
