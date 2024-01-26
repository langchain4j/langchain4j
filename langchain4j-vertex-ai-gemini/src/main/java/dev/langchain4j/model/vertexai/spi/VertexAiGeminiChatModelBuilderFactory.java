package dev.langchain4j.model.vertexai.spi;

import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder} instances.
 */
public interface VertexAiGeminiChatModelBuilderFactory extends Supplier<VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder> {
}
