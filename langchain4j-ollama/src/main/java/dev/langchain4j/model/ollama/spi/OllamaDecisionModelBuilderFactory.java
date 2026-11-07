package dev.langchain4j.model.ollama.spi;

import dev.langchain4j.model.ollama.OllamaDecisionModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link OllamaDecisionModel.OllamaDecisionModelBuilder} instances.
 */
public interface OllamaDecisionModelBuilderFactory extends Supplier<OllamaDecisionModel.OllamaDecisionModelBuilder> {
}
