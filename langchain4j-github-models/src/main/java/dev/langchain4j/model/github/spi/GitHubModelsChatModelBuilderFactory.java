package dev.langchain4j.model.github.spi;

import java.util.function.Supplier;

import dev.langchain4j.model.github.GitHubModelsChatModel;

/**
 * A factory for building {@link GitHubModelsChatModel.Builder} instances.
 */
public interface GitHubModelsChatModelBuilderFactory extends Supplier<GitHubModelsChatModel.Builder> {
}
