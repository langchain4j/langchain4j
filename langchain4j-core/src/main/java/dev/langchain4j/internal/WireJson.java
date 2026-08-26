package dev.langchain4j.internal;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Internal;
import dev.langchain4j.spi.json.WireJsonCodecFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for the JSON codec used by provider wire DTOs.
 *
 * @see WireJsonCodecFactory
 */
@Internal
public final class WireJson {

    /**
     * A codec per distinct spec. Most callers hold theirs in a static field and ask once, but some
     * build one per instance, and every call would otherwise run a full {@link java.util.ServiceLoader}
     * scan and construct another mapper. There are only ever a handful of distinct specs - that is
     * what {@link WireJsonSpec} being a value makes possible - so this does not grow.
     */
    private static final Map<WireJsonSpec, Json.JsonCodec> CODECS = new ConcurrentHashMap<>();

    private WireJson() {}

    public static Json.JsonCodec codec(WireJsonSpec spec) {
        return CODECS.computeIfAbsent(spec, WireJson::create);
    }

    private static Json.JsonCodec create(WireJsonSpec spec) {
        for (WireJsonCodecFactory factory : loadFactories(WireJsonCodecFactory.class)) {
            return factory.create(spec);
        }
        return new JacksonWireJsonCodec(spec);
    }
}
