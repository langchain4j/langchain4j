package dev.langchain4j.model.ark.spi;

import java.util.function.Supplier;

import dev.langchain4j.model.ark.ArkChatModel;

/**
 * A factory for building {@link ArkChatModel.ArkChatModelBuilder} instances.
 */
public interface ArkChatModelBuilderFactory extends Supplier<ArkChatModel.ArkChatModelBuilder> {
}
