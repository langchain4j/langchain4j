package dev.langchain4j.model.workersai.spi;

import dev.langchain4j.model.workersai.WorkersAiEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link WorkersAiEmbeddingModel.Builder} instances.
 */
public interface WorkersAiEmbeddingModelBuilderFactory extends Supplier<WorkersAiEmbeddingModel.Builder> {
}
