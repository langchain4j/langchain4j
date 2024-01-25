package dev.langchain4j.model.dashscope.spi;

import dev.langchain4j.model.dashscope.QwenLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QwenLanguageModel.QwenLanguageModelBuilder} instances.
 */
public interface QwenLanguageModelBuilderFactory extends Supplier<QwenLanguageModel.QwenLanguageModelBuilder> {
}
