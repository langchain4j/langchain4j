package dev.langchain4j.model.ark.spi;

import dev.langchain4j.model.ark.ArkLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link ArkLanguageModel.ArkLanguageModelBuilder} instances.
 */
public interface ArkLanguageModelBuilderFactory extends Supplier<ArkLanguageModel.ArkLanguageModelBuilder> {
}
