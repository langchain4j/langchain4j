package dev.langchain4j.reactive.streaming;

import dev.langchain4j.Internal;

/**
 * Shared defaults for the reactive ({@code Flow.Publisher}) streaming path used by streaming chat model providers.
 */
@Internal
public final class ReactiveStreamingDefaults {

    private ReactiveStreamingDefaults() {}

    /**
     * Default size of the bounded back-pressure buffer for the reactive streaming publisher (see
     * {@link HttpStreamingChatPublisher}). A follow-up may make this configurable per provider.
     */
    public static final int DEFAULT_BUFFER_SIZE = 16384;
}
