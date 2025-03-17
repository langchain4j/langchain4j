package dev.langchain4j.model.github.spi;

import dev.langchain4j.model.github.GitHubModelsEmbeddingModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link GitHubModelsEmbeddingModel.Builder} instances.
 */
public interface GitHubModelsEmbeddingModelBuilderFactory extends Supplier<GitHubModelsEmbeddingModel.Builder> {
}
