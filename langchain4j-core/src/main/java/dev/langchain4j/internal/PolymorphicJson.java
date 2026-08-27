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
        for (PolymorphicJsonCodecFactory factory : loadFactories(PolymorphicJsonCodecFactory.class)) {
            return factory.create(allowlist);
        }
        return new JacksonPolymorphicJsonCodec(allowlist);
    }
}
