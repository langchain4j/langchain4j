package dev.langchain4j.spi.observability;

import dev.langchain4j.observability.api.AiServiceListenerRegistrar;
import java.util.function.Supplier;

/**
 * A factory for providing a global {@link AiServiceListenerRegistrar} via Java SPI.
 *
 * <p>The registrar returned by {@link #get()} is used as a <em>delegate</em>: a fresh
 * per-agent {@link AiServiceListenerRegistrar} is always created for each agent context,
 * and events are forwarded to the delegate after notifying the agent's own listeners.
 * This means listeners registered on the delegate receive events from all agents, while
 * listeners registered on individual agent registrars remain isolated.
 *
 * <p>Implementations may return a singleton from {@link #get()}.
 */
public interface AiServiceListenerRegistrarFactory extends Supplier<AiServiceListenerRegistrar> {}
