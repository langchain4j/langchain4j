package dev.langchain4j.model.github.spi;

import dev.langchain4j.model.github.GitHubModelsStreamingChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link GitHubModelsStreamingChatModel.Builder} instances.
 */
public interface GitHubModelsStreamingChatModelBuilderFactory extends Supplier<GitHubModelsStreamingChatModel.Builder> {
}
