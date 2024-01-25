package dev.langchain4j.model.openai.spi;

import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder} instances.
 */
public interface OpenAiChatModelBuilderFactory extends Supplier<OpenAiChatModel.OpenAiChatModelBuilder> {
}
