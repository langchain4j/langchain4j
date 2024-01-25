package dev.langchain4j.model.dashscope.spi;

import dev.langchain4j.model.dashscope.QwenStreamingLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link QwenStreamingLanguageModel.QwenStreamingLanguageModelBuilder} instances.
 */
public interface QwenStreamingLanguageModelBuilderFactory extends Supplier<QwenStreamingLanguageModel.QwenStreamingLanguageModelBuilder> {
}
