package dev.langchain4j.spi.observability;

import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import java.util.function.Supplier;

/**
 * A factory for creating {@link AiServiceListenerRegistrar} instances.
 */
public interface AiServiceListenerRegistrarFactory extends Supplier<AiServiceListenerRegistrar> {}
