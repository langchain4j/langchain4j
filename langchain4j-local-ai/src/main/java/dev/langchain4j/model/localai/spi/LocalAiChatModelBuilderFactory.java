package dev.langchain4j.model.localai.spi;

import dev.langchain4j.model.localai.LocalAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link LocalAiChatModel.LocalAiChatModelBuilder} instances.
 */
public interface LocalAiChatModelBuilderFactory extends Supplier<LocalAiChatModel.LocalAiChatModelBuilder> {
}
