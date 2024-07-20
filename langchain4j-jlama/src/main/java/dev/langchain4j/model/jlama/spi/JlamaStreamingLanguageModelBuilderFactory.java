package dev.langchain4j.model.jlama.spi;

import dev.langchain4j.model.jlama.JlamaStreamingLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link JlamaStreamingLanguageModel.JlamaStreamingLanguageModelBuilder} instances.
 */
public interface JlamaStreamingLanguageModelBuilderFactory extends Supplier<JlamaStreamingLanguageModel.JlamaStreamingLanguageModelBuilder> {
}
