package dev.langchain4j.model.vertexai.spi;

import dev.langchain4j.model.vertexai.VertexAiImageModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link VertexAiImageModel.Builder} instances.
 */
public interface VertexAiImageModelBuilderFactory extends Supplier<VertexAiImageModel.Builder> {
}
