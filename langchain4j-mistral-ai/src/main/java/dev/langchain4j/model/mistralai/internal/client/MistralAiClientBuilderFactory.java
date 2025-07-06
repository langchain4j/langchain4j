package dev.langchain4j.model.mistralai.internal.client;

import dev.langchain4j.Internal;
import java.util.function.Supplier;

@Internal
@SuppressWarnings("rawtypes")
public interface MistralAiClientBuilderFactory extends Supplier<MistralAiClient.Builder> {
}
