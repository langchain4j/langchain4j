package dev.langchain4j.model.huggingface.spi;

import dev.langchain4j.model.huggingface.HuggingFaceChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link HuggingFaceChatModel.Builder} instances.
 */
public interface HuggingFaceChatModelBuilderFactory extends Supplier<HuggingFaceChatModel.Builder> {
}
