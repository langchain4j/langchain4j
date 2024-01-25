package dev.langchain4j.model.huggingface.spi;

import dev.langchain4j.model.huggingface.HuggingFaceLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link HuggingFaceLanguageModel.Builder} instances.
 */
public interface HuggingFaceLanguageModelBuilderFactory extends Supplier<HuggingFaceLanguageModel.Builder> {
}
