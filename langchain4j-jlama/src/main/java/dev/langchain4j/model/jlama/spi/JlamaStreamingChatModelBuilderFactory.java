package dev.langchain4j.model.jlama.spi;

import dev.langchain4j.model.jlama.JlamaStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link JlamaStreamingChatModel.JlamaStreamingChatModelBuilder} instances.
 */
public interface JlamaStreamingChatModelBuilderFactory extends Supplier<JlamaStreamingChatModel.JlamaStreamingChatModelBuilder> {
}
