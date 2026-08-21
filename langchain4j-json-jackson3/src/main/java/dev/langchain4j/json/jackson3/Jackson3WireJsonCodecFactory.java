package dev.langchain4j.json.jackson3;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJsonSpec;
import dev.langchain4j.spi.json.WireJsonCodecFactory;

public class Jackson3WireJsonCodecFactory implements WireJsonCodecFactory {

    @Override
    public Json.JsonCodec create(WireJsonSpec spec) {
        return new Jackson3WireJsonCodec(spec);
    }
}
