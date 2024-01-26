package dev.langchain4j.model.ollama.spi;

import dev.langchain4j.model.ollama.OllamaStreamingLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OllamaStreamingLanguageModel.OllamaStreamingLanguageModelBuilder} instances.
 */
public interface OllamaStreamingLanguageModelBuilderFactory extends Supplier<OllamaStreamingLanguageModel.OllamaStreamingLanguageModelBuilder> {
}
