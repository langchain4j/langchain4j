package dev.langchain4j.spi.audit;

import dev.langchain4j.audit.api.AiServiceInvocationEventListenerRegistrar;
import java.util.function.Supplier;

/**
 * A factory for creating {@link AiServiceInvocationEventListenerRegistrar} instances.
 */
public interface AiServiceInvocationEventListenerRegistrarFactory
        extends Supplier<AiServiceInvocationEventListenerRegistrar> {}
