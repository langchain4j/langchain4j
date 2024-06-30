package dev.langchain4j.model.jlama.spi;

import java.util.function.Supplier;

import dev.langchain4j.model.jlama.JlamaStreamingLanguageModel;

/**
 * A factory for building {@link JlamaStreamingLanguageModel.JlamaStreamingLanguageModelBuilder} instances.
 */
public interface JlamaStreamingLanguageModelBuilderFactory extends Supplier<JlamaStreamingLanguageModel.JlamaStreamingLanguageModelBuilder>
{
}
