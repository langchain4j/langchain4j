package dev.langchain4j.model.ollama.spi;

import dev.langchain4j.model.ollama.OllamaLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OllamaLanguageModel.OllamaLanguageModelBuilder} instances.
 */
public interface OllamaLanguageModelBuilderFactory extends Supplier<OllamaLanguageModel.OllamaLanguageModelBuilder> {
}
