package dev.langchain4j.model.ollama.spi;

import dev.langchain4j.model.ollama.OllamaChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OllamaChatModel.OllamaChatModelBuilder} instances.
 */
public interface OllamaChatModelBuilderFactory extends Supplier<OllamaChatModel.OllamaChatModelBuilder> {
}
