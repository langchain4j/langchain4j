package dev.langchain4j.model.openai.spi;

import dev.langchain4j.model.openai.OpenAiModerationModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiModerationModel.OpenAiModerationModelBuilder} instances.
 */
public interface OpenAiModerationModelBuilderFactory extends Supplier<OpenAiModerationModel.OpenAiModerationModelBuilder> {
}
