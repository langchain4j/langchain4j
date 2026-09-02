package dev.langchain4j.model.chat.response;

/**
 * Default {@link RawStreamingEvent} implementation, wrapping the raw provider event. Package-private;
 * created via {@link RawStreamingEvent#of(Object)}.
 *
 * @param rawEvent the raw provider streaming event; see {@link RawStreamingEvent#rawEvent()}
 */
record DefaultRawStreamingEvent(Object rawEvent) implements RawStreamingEvent {
}
