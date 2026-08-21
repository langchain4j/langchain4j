package dev.langchain4j.json.jackson3;

import dev.langchain4j.internal.Json;
import dev.langchain4j.spi.json.JsonCodecFactory;

public class Jackson3JsonCodecFactory implements JsonCodecFactory {

    @Override
    public Json.JsonCodec create() {
        return new Jackson3JsonCodec();
    }
}
