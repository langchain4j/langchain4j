package dev.langchain4j.model.chat.response;

import dev.langchain4j.Experimental;

/**
 * A provider-specific streaming event that langchain4j's generic event model does not (yet) map to a
 * dedicated {@link StreamingEvent} type — for example a web-search, citation or reasoning-summary event. TODO
 * This is an escape hatch: rather than silently dropping such events, a provider surfaces them as
 * {@code RawStreamingEvent} so callers can consume them — typically by parsing {@link #rawData()}
 * themselves — even though the library has no typed representation for them.
 * <p>
 * <b>Forward compatibility.</b> This is an interface on purpose. When such an event later graduates to a
 * dedicated typed event, that typed event is expected to <em>also</em> implement {@code RawStreamingEvent}
 * (carrying the same {@link #rawData()} and {@link #providerEventType()}). So code that matched on
 * {@code instanceof RawStreamingEvent} and parsed {@link #rawData()} keeps working after the promotion;
 * only code that wants the new typed view needs to opt into it.
 * <p>
 * <b>Stability.</b> The access mechanism ({@link #rawData()} / {@link #providerEventType()}) is stable, but
 * the <em>shape</em> of {@link #rawData()} is the provider's wire format and carries no stability guarantee:
 * the provider may change it at any time, and langchain4j does not shield callers from that.
 * <p>
 * Consumers must be prepared to receive event types they do not recognize and ignore them (see
 * {@link dev.langchain4j.model.chat.StreamingChatModel#chat(dev.langchain4j.model.chat.request.ChatRequest)}).
 *
 * @since 1.13.0
 */
@Experimental
public interface RawStreamingEvent extends StreamingEvent { // TODO name

    /**
     * A stable, best-effort discriminator for the kind of provider event (e.g. {@code "web_search"}), when
     * the provider names its events. May be {@code null} when the provider does not label the event.
     */
    String providerEventType(); // TODO name

    /**
     * The raw provider payload (typically the JSON of the server-sent event), for the caller to parse
     * manually. Its shape is the provider's wire format and is not guaranteed to be stable.
     */
    String rawData();
}
