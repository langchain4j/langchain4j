package dev.langchain4j.model.mistralai.spi;

import dev.langchain4j.model.mistralai.MistralAiCompletionModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link dev.langchain4j.model.mistralai.MistralAiCompletionModel.MistralAiCompletionModelBuilder} instances.
 */
public interface MistralAiCompletionModelBuilderFactory extends Supplier<MistralAiCompletionModel.MistralAiCompletionModelBuilder> {
}
