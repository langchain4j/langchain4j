package dev.langchain4j.model.vertexai.spi;

import dev.langchain4j.model.vertexai.VertexAiChatModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link VertexAiChatModel.Builder} instances.
 */
public interface VertexAiEmbeddingModelBuilderFactory extends Supplier<VertexAiEmbeddingModel.Builder> {
}
