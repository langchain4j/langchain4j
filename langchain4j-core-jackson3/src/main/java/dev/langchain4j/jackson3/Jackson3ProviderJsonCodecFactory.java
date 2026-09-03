package dev.langchain4j.jackson3;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.spi.json.ProviderJsonCodecFactory;

public class Jackson3ProviderJsonCodecFactory implements ProviderJsonCodecFactory {

    @Override
    public Json.JsonCodec create(ProviderJsonSpec spec) {
        return new Jackson3ProviderJsonCodec(spec);
    }
}
