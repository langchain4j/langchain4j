package dev.langchain4j.model.jlama.spi;

import dev.langchain4j.model.jlama.JlamaStreamingChatLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link JlamaStreamingChatLanguageModel.JlamaStreamingChatLanguageModelBuilder} instances.
 */
public interface JlamaStreamingChatLanguageModelBuilderFactory extends Supplier<JlamaStreamingChatLanguageModel.JlamaStreamingChatLanguageModelBuilder> {
}
