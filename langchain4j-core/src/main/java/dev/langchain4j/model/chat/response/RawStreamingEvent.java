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
 * event, exposed via {@link #rawEvent()}.
 *
 * @since 1.13.0
 */
@Experimental
public interface RawStreamingEvent extends StreamingEvent {

    /**
     * The raw provider streaming event that langchain4j does not map to a typed {@link StreamingEvent}.
     * <p>
     * Its type depends on the provider implementation: implementations built on the
     * {@code dev.langchain4j.http.client.HttpClient} abstraction (e.g., OpenAI) typically expose a
     * {@code ServerSentEvent}; SDK-based implementations may expose the SDK's own event object. The object's
     * shape is the provider's wire format and carries no stability guarantee.
     */
    Object rawEvent();
}
