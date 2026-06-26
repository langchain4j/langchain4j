package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;

/**
 * Default {@link RawStreamingEvent} implementation, wrapping the raw provider event.
 * Used to surface provider-specific streaming events that have no dedicated {@link StreamingEvent} type.
 *
 * @param rawEvent the raw provider streaming event; see {@link RawStreamingEvent#rawEvent()}
 * @since 1.13.0
 */
@Experimental
public record DefaultRawStreamingEvent(Object rawEvent) implements RawStreamingEvent {
}
