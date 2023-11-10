package dev.langchain4j.spi.data.message;

import dev.langchain4j.data.message.ChatMessageJsonCodec;

public interface ChatMessageJsonCodecFactory {

    ChatMessageJsonCodec create();
}
