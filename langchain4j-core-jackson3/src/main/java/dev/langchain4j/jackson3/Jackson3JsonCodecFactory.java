package dev.langchain4j.jackson3;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.spi.PrioritizedFactory;
import dev.langchain4j.spi.json.JsonCodecFactory;
import static dev.langchain4j.spi.PrioritizedFactory.YIELDS_TO_OTHERS;


@Internal
public class Jackson3JsonCodecFactory implements JsonCodecFactory, PrioritizedFactory {

    @Override
    public int priority() {
        return YIELDS_TO_OTHERS; // a framework that supplies its own codec keeps it
    }


    @Override
    public Json.JsonCodec create() {
        return new Jackson3JsonCodec();
    }
}
