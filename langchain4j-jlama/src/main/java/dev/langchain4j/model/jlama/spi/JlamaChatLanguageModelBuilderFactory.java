package dev.langchain4j.model.jlama.spi;

import java.util.function.Supplier;

import dev.langchain4j.model.jlama.JlamaChatLanguageModel;

/**
 * A factory for building {@link JlamaChatLanguageModel.JlamaChatLanguageModelBuilder} instances.
 */
public interface JlamaChatLanguageModelBuilderFactory extends Supplier<JlamaChatLanguageModel.JlamaChatLanguageModelBuilder>
{
}
