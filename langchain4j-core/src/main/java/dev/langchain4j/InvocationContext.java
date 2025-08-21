package dev.langchain4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO
 *
 * @since 1.4.0
 */
public class InvocationContext { // TODO name, module, package

    // TODO test for mutability

    private final Map<String, Object> context; // mutable on purpose
    // TODO concurrent

    public InvocationContext() {
        this.context = new ConcurrentHashMap<>();
    }

    public InvocationContext(Map<String, Object> context) {
        this.context = context; // TODO ensure it is concurrent
    }

    public Map<String, Object> asMap() { // TODO name
        return context;
    }

    public <T> T get(String key) {
        return (T) context.get(key);
    }

    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) context.getOrDefault(key, defaultValue);
    }

    public <T> void put(String key, T value) {
        context.put(key, value);
    }

    public boolean containsKey(String key) {
        return context.containsKey(key);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        InvocationContext that = (InvocationContext) object;
        return Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(context);
    }

    @Override
    public String toString() {
        return "InvocationContext{" + // TODO names
                "context=" + context +
                '}';
    }

    public static InvocationContext from(String key, Object value) {
        return new InvocationContext(Map.of(key, value));
    }
}
