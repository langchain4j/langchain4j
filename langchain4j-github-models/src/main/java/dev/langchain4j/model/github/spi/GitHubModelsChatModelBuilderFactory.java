package dev.langchain4j.model.github.spi;

import dev.langchain4j.model.github.GitHubModelsChatModel;

import java.util.function.Supplier;

/**
 * A factory for building {@link GitHubModelsChatModel.Builder} instances.
 *
 * @deprecated This module is deprecated and will be removed in a future release. Please use the langchain4j-openai-official module instead.
 */
@Deprecated(since = "1.10.0", forRemoval = true)
@SuppressWarnings("removal")
public interface GitHubModelsChatModelBuilderFactory extends Supplier<GitHubModelsChatModel.Builder> {
}
