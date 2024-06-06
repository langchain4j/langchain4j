package dev.langchain4j.model.mistralai.spi;

import dev.langchain4j.model.mistralai.MistralAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link dev.langchain4j.model.mistralai.MistralAiChatModel.MistralAiChatModelBuilder} instances.
 */
public interface MistralAiChatModelBuilderFactory extends Supplier<MistralAiChatModel.MistralAiChatModelBuilder> {
}
