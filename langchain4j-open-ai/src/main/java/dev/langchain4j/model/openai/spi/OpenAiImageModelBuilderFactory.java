package dev.langchain4j.model.openai.spi;

import dev.langchain4j.model.openai.OpenAiImageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OpenAiImageModel.OpenAiImageModelBuilder} instances.
 */
public interface OpenAiImageModelBuilderFactory extends Supplier<OpenAiImageModel.OpenAiImageModelBuilder> {
}
