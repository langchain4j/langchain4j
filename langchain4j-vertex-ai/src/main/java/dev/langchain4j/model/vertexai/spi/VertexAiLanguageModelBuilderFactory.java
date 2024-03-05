package dev.langchain4j.model.vertexai.spi;

import dev.langchain4j.model.vertexai.VertexAiLanguageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link VertexAiLanguageModel.Builder} instances.
 */
public interface VertexAiLanguageModelBuilderFactory extends Supplier<VertexAiLanguageModel.Builder> {
}
