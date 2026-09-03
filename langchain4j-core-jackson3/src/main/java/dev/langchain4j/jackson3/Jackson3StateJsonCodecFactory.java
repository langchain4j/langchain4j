package dev.langchain4j.jackson3;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.TypeAllowlist;
import dev.langchain4j.spi.json.StateJsonCodecFactory;

public class Jackson3StateJsonCodecFactory implements StateJsonCodecFactory {

    @Override
    public Json.JsonCodec create(TypeAllowlist allowlist, ClassLoader classLoader) {
        return new Jackson3StateJsonCodec(allowlist, classLoader);
    }
}
