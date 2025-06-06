package dev.langchain4j.model.vertexai.gemini.spi;

import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder} instances.
 */
public interface VertexAiGeminiChatModelBuilderFactory extends Supplier<VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder> {
}
