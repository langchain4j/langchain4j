package dev.langchain4j.internal;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Internal;
import dev.langchain4j.spi.json.PolymorphicJsonCodecFactory;

/**
 * Entry point for the JSON codec used where values are not known ahead of time and their types have
 * to be written into the document.
 *
 * <p>Kept apart from {@link WireJson}, which describes a wire format agreed with a remote service
 * and never writes type information.
 *
 * @see PolymorphicJsonCodecFactory
 */
@Internal
public final class PolymorphicJson {

    private PolymorphicJson() {}

    /**
     * A codec is built per call rather than cached, because a {@link TypeAllowlist} is mutable and
     * a caller registering a type expects that to apply to the codec it holds.
     */
    public static Json.JsonCodec codec(TypeAllowlist allowlist) {
        return codec(allowlist, null);
    }

    /**
     * @param classLoader resolves the type names found in the document, or null to leave that to
     *                    the JSON library. A codec cannot be told this after it is built - one of
     *                    the two Jackson versions has no way to reconfigure a mapper - so changing
     *                    it means asking for another codec.
     */
    public static Json.JsonCodec codec(TypeAllowlist allowlist, ClassLoader classLoader) {
        for (PolymorphicJsonCodecFactory factory : loadFactories(PolymorphicJsonCodecFactory.class)) {
            return factory.create(allowlist, classLoader);
        }
        return new JacksonPolymorphicJsonCodec(allowlist, classLoader);
    }
}
