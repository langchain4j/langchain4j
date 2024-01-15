package dev.langchain4j.spi.data.message;

import dev.langchain4j.data.message.ChatMessageJsonCodec;

/**
 * A factory for creating {@link ChatMessageJsonCodec} objects.
 * Used for SPI.
 */
public interface ChatMessageJsonCodecFactory {
    /**
     * Creates a new {@link ChatMessageJsonCodec} object.
     * @return the new {@link ChatMessageJsonCodec} object.
     */
    ChatMessageJsonCodec create();
}
