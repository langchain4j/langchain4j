package dev.langchain4j.model.ark.spi;

import java.util.function.Supplier;

import dev.langchain4j.model.ark.ArkStreamingChatModel;

/**
 * A factory for building {@link ArkStreamingChatModel.ArkStreamingChatModelBuilder} instances.
 */
public interface ArkStreamingChatModelBuilderFactory extends Supplier<ArkStreamingChatModel.ArkStreamingChatModelBuilder> {
}
