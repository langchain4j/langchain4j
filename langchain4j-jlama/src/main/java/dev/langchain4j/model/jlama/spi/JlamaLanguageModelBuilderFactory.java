package dev.langchain4j.model.jlama.spi;

import java.util.function.Supplier;

import dev.langchain4j.model.jlama.JlamaLanguageModel;

/**
 * A factory for building {@link JlamaLanguageModel.JlamaLanguageModelBuilder} instances.
 */
public interface JlamaLanguageModelBuilderFactory extends Supplier<JlamaLanguageModel.JlamaLanguageModelBuilder>
{
}
