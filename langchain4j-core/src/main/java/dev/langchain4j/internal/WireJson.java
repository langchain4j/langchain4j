package dev.langchain4j.internal;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.Internal;
import dev.langchain4j.spi.json.WireJsonCodecFactory;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point for the JSON codec used by provider wire DTOs.
 *
 * @see WireJsonCodecFactory
 */
@Internal
public final class WireJson {

    /**
     * A codec per distinct spec, per class loader. Most callers hold theirs in a static field and
     * ask once, but some build one per instance, and every call would otherwise run a full
     * {@link java.util.ServiceLoader} scan and construct another mapper. There are only ever a
     * handful of distinct specs - that is what {@link WireJsonSpec} being a value makes possible.
     *
     * <p>Keyed by class loader as well, because the codec is resolved through the thread-context
     * loader: several applications sharing one copy of LangChain4j from a container's common
     * directory each get their own, rather than whichever one asked first. The keys are weak so
     * that caching here does not stop an application's loader being collected when it is undeployed.
     */
    private static final Map<ClassLoader, Map<WireJsonSpec, Json.JsonCodec>> CODECS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private WireJson() {}

    public static Json.JsonCodec codec(WireJsonSpec spec) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return CODECS.computeIfAbsent(classLoader, loader -> new ConcurrentHashMap<>())
                .computeIfAbsent(spec, WireJson::create);
    }

    private static Json.JsonCodec create(WireJsonSpec spec) {
        for (WireJsonCodecFactory factory : loadFactories(WireJsonCodecFactory.class)) {
            return factory.create(spec);
        }
        return new JacksonWireJsonCodec(spec);
    }
}
