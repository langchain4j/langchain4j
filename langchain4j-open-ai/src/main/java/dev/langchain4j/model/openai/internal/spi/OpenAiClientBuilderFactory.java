package dev.langchain4j.model.openai.internal.spi;

import dev.langchain4j.model.openai.internal.OpenAiClient;

import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public interface OpenAiClientBuilderFactory extends Supplier<OpenAiClient.Builder> {
}
