package dev.langchain4j.jackson3;

import dev.langchain4j.data.message.ChatMessageJsonCodec;
import dev.langchain4j.spi.data.message.ChatMessageJsonCodecFactory;

public class Jackson3ChatMessageJsonCodecFactory implements ChatMessageJsonCodecFactory {

    @Override
    public ChatMessageJsonCodec create() {
        return new Jackson3ChatMessageJsonCodec();
    }
}
