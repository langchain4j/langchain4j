package dev.langchain4j.model.ollama.spi;

import dev.langchain4j.model.ollama.OllamaStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OllamaStreamingChatModel.OllamaStreamingChatModelBuilder} instances.
 */
public interface OllamaStreamingChatModelBuilderFactory extends Supplier<OllamaStreamingChatModel.OllamaStreamingChatModelBuilder> {
}
