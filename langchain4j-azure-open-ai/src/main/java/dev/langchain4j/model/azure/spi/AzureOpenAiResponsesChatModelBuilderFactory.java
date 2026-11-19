package dev.langchain4j.model.azure.spi;

import dev.langchain4j.model.azure.AzureOpenAiResponsesChatModel;
import java.util.function.Supplier;

/**
 * A factory for building {@link AzureOpenAiResponsesChatModel.Builder} instances.
 */
public interface AzureOpenAiResponsesChatModelBuilderFactory extends Supplier<AzureOpenAiResponsesChatModel.Builder> {}
