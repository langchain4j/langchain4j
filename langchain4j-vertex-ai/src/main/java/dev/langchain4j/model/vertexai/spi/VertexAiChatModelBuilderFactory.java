package dev.langchain4j.model.vertexai.spi;

import dev.langchain4j.model.vertexai.VertexAiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link VertexAiChatModel.Builder} instances.
 */
public interface VertexAiChatModelBuilderFactory extends Supplier<VertexAiChatModel.Builder> {
}
