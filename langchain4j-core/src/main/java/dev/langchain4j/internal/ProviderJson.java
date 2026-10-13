package dev.langchain4j.internal;

import static dev.langchain4j.spi.ServiceHelper.loadFactory;

import dev.langchain4j.Internal;
import dev.langchain4j.spi.json.ProviderJsonCodecFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for the JSON codec used by provider wire DTOs.
 *
 * @see ProviderJsonCodecFactory
 */
@Internal
public final class ProviderJson {

    /**
     * A codec per distinct spec. Most callers hold theirs in a static field and ask once, but some
     * build one per instance, and every call would otherwise run a full {@link java.util.ServiceLoader}
     * scan and construct another mapper. There are only ever a handful of distinct specs - that is
     * what {@link ProviderJsonSpec} being a value makes possible.
     */
    private static final Map<ProviderJsonSpec, Json.JsonCodec> CODECS = new ConcurrentHashMap<>();

    private ProviderJson() {}

    public static Json.JsonCodec codec(ProviderJsonSpec spec) {
        return CODECS.computeIfAbsent(spec, ProviderJson::create);
    }

    private static Json.JsonCodec create(ProviderJsonSpec spec) {
        ProviderJsonCodecFactory factory = loadFactory(ProviderJsonCodecFactory.class);
        return factory != null ? factory.create(spec) : new JacksonProviderJsonCodec(spec);
    }
}
