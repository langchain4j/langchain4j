package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;

/**
 * A provider-specific streaming event that langchain4j's generic event model does not (yet) map to a
 * dedicated {@link StreamingEvent} type — for example a web-search, citation or reasoning-summary event.
 * This is an escape hatch: rather than silently dropping such events, a provider surfaces them as
 * {@code RawStreamingEvent} so callers can consume them — typically by inspecting {@link #rawEvent()}
 * themselves — even though the library has no typed representation for them.
 * <p>
 * Consumers must be prepared to receive event types they do not recognize and ignore them (see
 * {@link dev.langchain4j.model.chat.StreamingChatModel#chat(dev.langchain4j.model.chat.request.ChatRequest)}).
 * <p>
 * This is the reactive ({@link StreamingEvent} publisher) counterpart of the handler-based
 * {@link StreamingChatResponseHandler#onUnmappedRawEvent(Object)} callback: it carries the same raw provider
 * event.
 * <p>
 * <b>Forward compatibility.</b> This is an interface on purpose. If such an event later graduates to a
 * dedicated typed event, that typed event can also implement
 * {@code RawStreamingEvent} (exposing the same {@link #rawEvent()}). A single emitted object then satisfies
 * both audiences: code that matched {@code instanceof RawStreamingEvent} and inspected {@link #rawEvent()}
 * keeps working, while new code can match the typed event and use its typed accessors.
 *
 * @since 1.13.0
 */
@Experimental
public interface RawStreamingEvent extends StreamingEvent {

    /**
     * The raw provider streaming event that LangChain4j does not map to a typed {@link StreamingEvent}.
     * <p>
     * Its type depends on the provider implementation: implementations built on the
     * {@code dev.langchain4j.http.client.HttpClient} abstraction (e.g., OpenAI) typically expose a
     * {@code ServerSentEvent}; SDK-based implementations may expose the SDK's own event object. The object's
     * shape is the provider's wire format and carries no stability guarantee.
     */
    Object rawEvent();

    /**
     * Creates a {@link RawStreamingEvent} wrapping the given raw provider event.
     *
     * @param rawEvent the raw provider streaming event; see {@link #rawEvent()}
     * @return a {@link RawStreamingEvent} carrying {@code rawEvent}
     */
    static RawStreamingEvent of(Object rawEvent) {
        return new DefaultRawStreamingEvent(rawEvent);
    }
}
