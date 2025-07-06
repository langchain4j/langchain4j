package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.Internal;

import java.util.function.Supplier;

@Internal
@SuppressWarnings("rawtypes")
public interface AnthropicClientBuilderFactory extends Supplier<AnthropicClient.Builder> {
}
