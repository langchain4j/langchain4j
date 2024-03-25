package dev.langchain4j.model.mistralai.spi;

import dev.langchain4j.model.mistralai.MistralAiModels;

import java.util.function.Supplier;

/**
 * A factory for building {@link MistralAiModels.MistralAiModelsBuilder} instances.
 */
public interface MistralAiModelsBuilderFactory extends Supplier<MistralAiModels.MistralAiModelsBuilder> {
}
