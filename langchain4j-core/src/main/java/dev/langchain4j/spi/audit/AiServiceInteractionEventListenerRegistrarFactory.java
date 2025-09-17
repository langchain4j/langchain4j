package dev.langchain4j.spi.audit;

import dev.langchain4j.audit.api.AiServiceInteractionEventListenerRegistrar;
import java.util.function.Supplier;

/**
 * A factory for creating {@link AiServiceInteractionEventListenerRegistrar} instances.
 */
public interface AiServiceInteractionEventListenerRegistrarFactory
        extends Supplier<AiServiceInteractionEventListenerRegistrar> {}
