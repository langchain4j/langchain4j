package dev.langchain4j.model.ark.spi;

import java.util.function.Supplier;

import dev.langchain4j.model.ark.ArkStreamingLanguageModel;

/**
 * A factory for building {@link ArkStreamingLanguageModel.ArkStreamingLanguageModelBuilder} instances.
 */
public interface ArkStreamingLanguageModelBuilderFactory extends Supplier<ArkStreamingLanguageModel.ArkStreamingLanguageModelBuilder> {
}
