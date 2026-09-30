package dev.langchain4j.jackson3;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.ChatMessageJsonCodec;
import dev.langchain4j.spi.PrioritizedFactory;
import dev.langchain4j.spi.data.message.ChatMessageJsonCodecFactory;
import static dev.langchain4j.spi.PrioritizedFactory.YIELDS_TO_OTHERS;


@Internal
public class Jackson3ChatMessageJsonCodecFactory implements ChatMessageJsonCodecFactory, PrioritizedFactory {

    @Override
    public int priority() {
        return YIELDS_TO_OTHERS; // a framework that supplies its own codec keeps it
    }


    @Override
    public ChatMessageJsonCodec create() {
        return new Jackson3ChatMessageJsonCodec();
    }
}
