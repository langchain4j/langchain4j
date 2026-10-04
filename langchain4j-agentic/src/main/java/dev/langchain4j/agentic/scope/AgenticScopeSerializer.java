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
        return new DefaultAgenticScopeJsonCodec();
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
        if (!CODEC.allowPackagePrefix(packagePrefix)) {
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
        if (!CODEC.allowType(type)) {
            LOG.warn("allowDeserializationType has no effect: the active codec ({}) does not support type allowlisting", CODEC.getClass().getName());
        }
    }

    /**
     * Sets the {@link ClassLoader} used to resolve types during deserialization
     * of {@link AgenticScope} state.
     * <p>
     * By default, the deserializer uses the parent {@link ClassLoader}. In
     * environments where domain types are loaded by a different classloader
     * , you must set the appropriate {@link ClassLoader} before calling {@link #fromJson(String)},
     * otherwise deserialization will fail with a {@link ClassNotFoundException}.
     * <p>
     * The setting is process-wide: it applies to every subsequent deserialization
     * in the JVM.
     *
     * @param classloader the class loader to use for resolving types
     * @see #registerForDeserializationPackageOf(Class)
     */
    public static void withClassLoader(ClassLoader classloader) {
        if (!CODEC.withClassLoader(classloader)) {
            LOG.warn("withClassLoader has no effect: the active codec ({}) does not support setting the classloader", CODEC.getClass().getName());
        }
    }

    /**
     * Convenience method that registers the package of the given class for
     * deserialization and sets the class loader used to resolve types.
     * <p>
     * This is equivalent to calling:
     * <pre>{@code
     * AgenticScopeSerializer.allowDeserializationPackagePrefix(type.getPackageName() + ".");
     * AgenticScopeSerializer.withClassLoader(type.getClassLoader());
     * }</pre>
     * <p>
     * Use this when your agents store domain objects in the {@link AgenticScope}
     * state and you want to allow all types in the same package with a single call:
     * <pre>{@code
     * AgenticScopeSerializer.registerForDeserializationPackageOf(Order.class);
     * }</pre>
     * <p>
     * Registrations are process-wide and permanent. Register the package before
     * the {@link #fromJson(String)} call that encounters its types, otherwise
     * that call fails with {@link UnserializableAgenticScopeException}.
     *
     * @param type a class whose package will be allowed and whose class loader
     *             will be used for type resolution
     * @see #allowDeserializationPackagePrefix(String)
     * @see #withClassLoader(ClassLoader)
     */
    public static void registerForDeserializationPackageOf(Class<?> type) {
        allowDeserializationPackagePrefix(type.getPackageName() + ".");
        withClassLoader(type.getClassLoader());
    }

}
