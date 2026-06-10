package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;

/**
 * Default {@link RawStreamingEvent} implementation, carrying a provider event type and the raw payload.
 * Used to surface provider-specific streaming events that have no dedicated {@link StreamingEvent} type.
 *
 * @since 1.13.0
 */
@Experimental
public record DefaultRawStreamingEvent(String providerEventType, String rawData) implements RawStreamingEvent { // TODO names
}
