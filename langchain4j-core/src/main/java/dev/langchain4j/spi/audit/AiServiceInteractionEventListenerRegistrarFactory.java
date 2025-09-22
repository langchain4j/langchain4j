package dev.langchain4j.spi.audit;

import java.util.function.Supplier;
import dev.langchain4j.audit.api.AiServiceInvocationEventListenerRegistrar;

/**
 * A factory for creating {@link AiServiceInvocationEventListenerRegistrar} instances.
 */
public interface AiServiceInteractionEventListenerRegistrarFactory
        extends Supplier<AiServiceInvocationEventListenerRegistrar> {}
