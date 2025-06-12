package dev.langchain4j.model.openai.spi;

import dev.langchain4j.Internal;
import dev.langchain4j.model.openai.OpenAiLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiLanguageModel.OpenAiLanguageModelBuilder} instances.
 */
@Internal
public interface OpenAiLanguageModelBuilderFactory extends Supplier<OpenAiLanguageModel.OpenAiLanguageModelBuilder> {
}
