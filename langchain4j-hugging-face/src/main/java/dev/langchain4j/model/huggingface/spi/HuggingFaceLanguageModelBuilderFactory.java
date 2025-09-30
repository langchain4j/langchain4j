package dev.langchain4j.model.huggingface.spi;

import dev.langchain4j.model.huggingface.HuggingFaceLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link HuggingFaceLanguageModel.Builder} instances.
 */
@Deprecated(forRemoval = true, since = "1.7.0-beta13")
public interface HuggingFaceLanguageModelBuilderFactory extends Supplier<HuggingFaceLanguageModel.Builder> {
}
