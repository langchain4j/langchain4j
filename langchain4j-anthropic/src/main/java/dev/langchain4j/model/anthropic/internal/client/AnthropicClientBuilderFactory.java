package dev.langchain4j.model.anthropic.internal.client;

import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public interface AnthropicClientBuilderFactory extends Supplier<AnthropicClient.Builder> {
}
