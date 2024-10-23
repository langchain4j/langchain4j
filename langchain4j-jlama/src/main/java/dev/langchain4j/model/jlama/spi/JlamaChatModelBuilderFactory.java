package dev.langchain4j.model.jlama.spi;

import dev.langchain4j.model.jlama.JlamaChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link JlamaChatModel.JlamaChatModelBuilder} instances.
 */
public interface JlamaChatModelBuilderFactory extends Supplier<JlamaChatModel.JlamaChatModelBuilder> {
}
