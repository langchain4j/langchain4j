package dev.langchain4j.model.jlama.spi;

import java.util.function.Supplier;

import dev.langchain4j.model.jlama.JlamaStreamingChatLanguageModel;

/**
 * A factory for building {@link JlamaStreamingChatLanguageModel.JlamaStreamingChatLanguageModelBuilder} instances.
 */
public interface JlamaStreamingChatLanguageModelBuilderFactory extends Supplier<JlamaStreamingChatLanguageModel.JlamaStreamingChatLanguageModelBuilder>
{
}
