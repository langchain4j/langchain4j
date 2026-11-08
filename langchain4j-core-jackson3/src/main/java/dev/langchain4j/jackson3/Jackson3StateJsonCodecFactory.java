package dev.langchain4j.jackson3;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.TypeAllowlist;
import dev.langchain4j.spi.PrioritizedFactory;
import dev.langchain4j.spi.json.StateJsonCodecFactory;
import static dev.langchain4j.spi.PrioritizedFactory.YIELDS_TO_OTHERS;


@Internal
public class Jackson3StateJsonCodecFactory implements StateJsonCodecFactory, PrioritizedFactory {

    @Override
    public int priority() {
        return YIELDS_TO_OTHERS; // a framework that supplies its own codec keeps it
    }


    @Override
    public Json.JsonCodec create(TypeAllowlist allowlist, ClassLoader classLoader) {
        return new Jackson3StateJsonCodec(allowlist, classLoader);
    }
}
