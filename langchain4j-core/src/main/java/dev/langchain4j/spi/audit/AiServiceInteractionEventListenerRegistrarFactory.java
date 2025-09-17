package dev.langchain4j.spi.audit;

import java.util.function.Supplier;
import dev.langchain4j.audit.api.AiServiceInteractionEventListenerRegistrar;

/**
 * A factory for creating {@link AiServiceInteractionEventListenerRegistrar} instances.
 */
public interface AiServiceInteractionEventListenerRegistrarFactory extends Supplier<AiServiceInteractionEventListenerRegistrar> {}
