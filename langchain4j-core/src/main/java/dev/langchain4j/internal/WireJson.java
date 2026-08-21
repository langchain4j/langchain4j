package dev.langchain4j.internal;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Internal;
import dev.langchain4j.spi.json.WireJsonCodecFactory;

/**
 * Entry point for the JSON codec used by provider wire DTOs.
 *
 * @see WireJsonCodecFactory
 */
@Internal
public final class WireJson {

    private WireJson() {}

    public static Json.JsonCodec codec(WireJsonSpec spec) {
        for (WireJsonCodecFactory factory : loadFactories(WireJsonCodecFactory.class)) {
            return factory.create(spec);
        }
        return new JacksonWireJsonCodec(spec);
    }
}
