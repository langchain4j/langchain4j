package dev.langchain4j.model.sparkdesk.spi;

import dev.langchain4j.model.sparkdesk.SparkdeskAiEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link SparkdeskAiEmbeddingModel.SparkdeskAiEmbeddingModelBuilder} instances.
 */
public interface SparkdeskAiEmbeddingModelBuilderFactory extends Supplier<SparkdeskAiEmbeddingModel.SparkdeskAiEmbeddingModelBuilder> {
}
