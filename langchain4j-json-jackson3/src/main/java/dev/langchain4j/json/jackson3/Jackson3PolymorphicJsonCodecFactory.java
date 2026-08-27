package dev.langchain4j.json.jackson3;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.TypeAllowlist;
import dev.langchain4j.spi.json.PolymorphicJsonCodecFactory;

public class Jackson3PolymorphicJsonCodecFactory implements PolymorphicJsonCodecFactory {

    @Override
    public Json.JsonCodec create(TypeAllowlist allowlist) {
        return new Jackson3PolymorphicJsonCodec(allowlist);
    }
}
