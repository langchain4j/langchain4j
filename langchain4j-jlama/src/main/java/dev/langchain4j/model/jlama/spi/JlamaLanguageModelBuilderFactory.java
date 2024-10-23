package dev.langchain4j.model.jlama.spi;

import dev.langchain4j.model.jlama.JlamaLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link JlamaLanguageModel.JlamaLanguageModelBuilder} instances.
 */
public interface JlamaLanguageModelBuilderFactory extends Supplier<JlamaLanguageModel.JlamaLanguageModelBuilder> {
}
