package dev.langchain4j.model.localai.spi;

import dev.langchain4j.model.localai.LocalAiLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link LocalAiLanguageModel.LocalAiLanguageModelBuilder} instances.
 */
public interface LocalAiLanguageModelBuilderFactory extends Supplier<LocalAiLanguageModel.LocalAiLanguageModelBuilder> {
}
