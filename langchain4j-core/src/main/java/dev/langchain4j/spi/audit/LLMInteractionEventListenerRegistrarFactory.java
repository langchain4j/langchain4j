package dev.langchain4j.spi.audit;

import dev.langchain4j.audit.api.LLMInteractionEventListenerRegistrar;
import java.util.function.Supplier;

/**
 * A factory for creating {@link LLMInteractionEventListenerRegistrar} instances.
 */
public interface LLMInteractionEventListenerRegistrarFactory extends Supplier<LLMInteractionEventListenerRegistrar> {}
