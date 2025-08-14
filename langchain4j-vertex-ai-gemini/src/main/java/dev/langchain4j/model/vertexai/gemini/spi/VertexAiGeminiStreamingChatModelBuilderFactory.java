package dev.langchain4j.model.vertexai.gemini.spi;

import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link VertexAiGeminiStreamingChatModel.VertexAiGeminiStreamingChatModelBuilder} instances.
 */
public interface VertexAiGeminiStreamingChatModelBuilderFactory extends Supplier<VertexAiGeminiStreamingChatModel.VertexAiGeminiStreamingChatModelBuilder> {
}
