package dev.langchain4j.model.ovhai.internal.client;

import java.util.function.Supplier;

/**
 * @deprecated Do not use anymore, use {@code langchain4j-open-ai} module instead
 */
@Deprecated(forRemoval = true, since = "1.14.0")
@SuppressWarnings("rawtypes")
public interface OvhAiClientBuilderFactory extends Supplier<OvhAiClient.Builder> {
}
