package dev.langchain4j.model.openai.spi;

import dev.langchain4j.model.openai.OpenAiStreamingLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiStreamingLanguageModel.OpenAiStreamingLanguageModelBuilder} instances.
 */
public interface OpenAiStreamingLanguageModelBuilderFactory extends Supplier<OpenAiStreamingLanguageModel.OpenAiStreamingLanguageModelBuilder> {
}
