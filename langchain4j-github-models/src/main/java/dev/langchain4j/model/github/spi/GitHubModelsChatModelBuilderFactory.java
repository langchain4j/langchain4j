package dev.langchain4j.model.github.spi;

import dev.langchain4j.model.github.GitHubModelsChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link GitHubModelsChatModel.Builder} instances.
 */
public interface GitHubModelsChatModelBuilderFactory extends Supplier<GitHubModelsChatModel.Builder> {
}
