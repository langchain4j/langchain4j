package dev.langchain4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO
 *
 * @since 1.5.0
 */
public class ExtraParameters { // TODO name, module, package

    private final ConcurrentHashMap<String, Object> map; // mutable on purpose

    public ExtraParameters() {
        this(new ConcurrentHashMap<>());
    }

    public ExtraParameters(Map<String, Object> map) {
        if (map instanceof ConcurrentHashMap<String, Object> concurrentHashMap) {
            this.map = concurrentHashMap; // TODO make a copy anyway?
        } else {
            this.map = new ConcurrentHashMap<>(map);
        }
    }

    public Map<String, Object> asMap() { // TODO name
        return map;
    }

    public <T> T get(String key) {
        return (T) map.get(key);
    }

    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) map.getOrDefault(key, defaultValue);
    }

    public <T> void put(String key, T value) {
        map.put(key, value);
    }

    public boolean containsKey(String key) {
        return map.containsKey(key);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ExtraParameters that = (ExtraParameters) object;
        return Objects.equals(map, that.map);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(map);
    }

    @Override
    public String toString() {
        return "ExtraParameters{" + // TODO names
                "map=" + map +
                '}';
    }

    public static ExtraParameters from(String key, Object value) {
        return new ExtraParameters(Map.of(key, value));
    }

    public static ExtraParameters from(Map<String, Object> map) {
        return new ExtraParameters(map);
    }
}
