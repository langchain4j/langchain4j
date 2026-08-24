package dev.langchain4j.agentic.scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * Utility class for serializing AgenticScope objects to JSON format.
 */
public class AgenticScopeSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticScopeSerializer.class);

    static final AgenticScopeJsonCodec CODEC = loadCodec();

    private AgenticScopeSerializer() { }

    private static AgenticScopeJsonCodec loadCodec() {
        for (AgenticScopeJsonCodec codec : ServiceLoader.load(AgenticScopeJsonCodec.class)) {
            return codec;
        }
        return new JacksonAgenticScopeJsonCodec();
    }

    /**
     * Serializes a AgenticScope into a JSON string.
     *
     * @param agenticScope AgenticScope to be serialized.
     * @return A JSON string with the contents of the AgenticScope.
     * @see AgenticScopeSerializer For details on deserialization.
     */
    public static String toJson(DefaultAgenticScope agenticScope) {
        return CODEC.toJson(agenticScope);
    }

    /**
     * Deserializes a JSON string into a AgenticScope object.
     *
     * @param json JSON string to be deserialized.
     * @return A AgenticScope object constructed from the JSON.
     * @see AgenticScopeSerializer For details on serialization.
     */
    public static DefaultAgenticScope fromJson(String json) {
        return CODEC.fromJson(json);
    }

    /**
     * Registers an additional type prefix (typically a package name) to be allowed
     * during deserialization of {@link AgenticScope} state.
     * <p>
     * By default, the deserializer allows standard JDK types ({@code java.util.*},
     * {@code java.math.*}, primitive wrappers). If your agents store domain objects
     * in the agentic scope state, you must register their package prefix before
     * deserialization occurs:
     * <pre>{@code
     * AgenticScopeSerializer.allowDeserializationPackagePrefix("com.acme.");
     * }</pre>
     * <p>
     * Registrations are process-wide and permanent: they apply to every subsequent
     * deserialization in the JVM and cannot be removed. In tests this means a prefix
     * registered by one test stays allowed for all later tests.
     * <p>
     * Registration is not retroactive: a type must be registered before the
     * {@link #fromJson(String)} call that encounters it, otherwise that call fails
     * with {@link UnserializableAgenticScopeException}. It takes effect immediately
     * for all subsequent deserializations.
     *
     * @param packagePrefix the package prefix to allow (e.g. {@code "com.acme."})
     * @throws IllegalArgumentException if the prefix is null or empty
     * @see #allowDeserializationType(Class)
     */
    public static void allowDeserializationPackagePrefix(String packagePrefix) {
        if (CODEC instanceof JacksonAgenticScopeJsonCodec) {
            JacksonAgenticScopeJsonCodec.PTV.addAllowedPrefix(packagePrefix);
        } else {
            LOG.warn("allowDeserializationPackagePrefix has no effect: the active codec ({}) does not support type allowlisting", CODEC.getClass().getName());
        }
    }

    /**
     * Registers a single class to be allowed during deserialization of
     * {@link AgenticScope} state.
     * <pre>{@code
     * AgenticScopeSerializer.allowDeserializationType(Order.class);
     * }</pre>
     * <p>
     * Registrations are process-wide and permanent: they apply to every subsequent
     * deserialization in the JVM and cannot be removed. Register the type before the
     * {@link #fromJson(String)} call that encounters it, otherwise that call fails
     * with {@link UnserializableAgenticScopeException}.
     *
     * @param type the class to allow
     * @see #allowDeserializationPackagePrefix(String)
     */
    public static void allowDeserializationType(Class<?> type) {
        if (CODEC instanceof JacksonAgenticScopeJsonCodec) {
            JacksonAgenticScopeJsonCodec.PTV.addAllowedClass(type.getName());
        } else {
            LOG.warn("allowDeserializationType has no effect: the active codec ({}) does not support type allowlisting", CODEC.getClass().getName());
        }
    }
}
