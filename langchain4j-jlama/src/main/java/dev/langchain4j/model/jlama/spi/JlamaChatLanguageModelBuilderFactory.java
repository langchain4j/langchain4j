package dev.langchain4j.model.jlama.spi;

import dev.langchain4j.model.jlama.JlamaChatLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link JlamaChatLanguageModel.JlamaChatLanguageModelBuilder} instances.
 */
public interface JlamaChatLanguageModelBuilderFactory extends Supplier<JlamaChatLanguageModel.JlamaChatLanguageModelBuilder> {
}
