package dev.langchain4j.spi.json;

import dev.langchain4j.internal.Json;

/**
 * A factory for creating {@link Json.JsonCodec} instances through SPI.
 */
public interface JsonCodecFactory {
    /**
     * Create a new {@link Json.JsonCodec}.
     * @return the new {@link Json.JsonCodec}.
     */
    Json.JsonCodec create();
}
