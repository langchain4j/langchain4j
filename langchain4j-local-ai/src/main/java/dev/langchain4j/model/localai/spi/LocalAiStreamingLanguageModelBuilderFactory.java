package dev.langchain4j.model.localai.spi;

import dev.langchain4j.model.localai.LocalAiStreamingLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link LocalAiStreamingLanguageModel.LocalAiStreamingLanguageModelBuilder} instances.
 */
public interface LocalAiStreamingLanguageModelBuilderFactory extends Supplier<LocalAiStreamingLanguageModel.LocalAiStreamingLanguageModelBuilder> {
}
