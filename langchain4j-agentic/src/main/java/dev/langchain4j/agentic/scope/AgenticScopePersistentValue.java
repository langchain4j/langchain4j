package dev.langchain4j.agentic.scope;

import dev.langchain4j.agentic.internal.DelayedResponse;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.service.TokenStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AgenticScopePersistentValue {

    private AgenticScopePersistentValue() {}

    static Object sanitize(Object value) {
        return sanitize(value, new IdentityHashMap<>());
    }

    private static Object sanitize(Object value, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || value instanceof PendingResponse<?>) {
            return value;
        }
        if (value instanceof DelayedResponse<?> delayedResponse) {
            return delayedResponse.isDone() ? sanitize(delayedResponse.blockingGet(), visited) : null;
        }
        if (value instanceof TokenStream) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map, visited);
        }
        if (value instanceof Collection<?> collection) {
            return sanitizeCollection(collection, visited);
        }
        if (value.getClass().isArray()) {
            return sanitizeArray(value, visited);
        }
        return value;
    }

    private static Object sanitizeMap(Map<?, ?> map, IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(map, Boolean.TRUE) != null) {
            return null;
        }
        try {
            boolean changed = false;
            Map<Object, Object> persistentMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                Object persistentValue = sanitize(value, visited);
                changed |= persistentValue != value;
                if (persistentValue != null || value == null) {
                    persistentMap.put(entry.getKey(), persistentValue);
                }
            }
            return changed ? persistentMap : map;
        } finally {
            visited.remove(map);
        }
    }

    private static Object sanitizeCollection(Collection<?> collection, IdentityHashMap<Object, Boolean> visited) {
        if (visited.put(collection, Boolean.TRUE) != null) {
            return null;
        }
        try {
            boolean changed = false;
            List<Object> persistentCollection = new ArrayList<>(collection.size());
            for (Object value : collection) {
                Object persistentValue = sanitize(value, visited);
                changed |= persistentValue != value;
                persistentCollection.add(persistentValue);
            }
            return changed ? persistentCollection : collection;
        } finally {
            visited.remove(collection);
        }
    }

    private static Object sanitizeArray(Object array, IdentityHashMap<Object, Boolean> visited) {
        if (array.getClass().getComponentType().isPrimitive()) {
            return array;
        }
        if (visited.put(array, Boolean.TRUE) != null) {
            return null;
        }
        try {
            int length = Array.getLength(array);
            boolean changed = false;
            Object[] persistentArray = new Object[length];
            for (int i = 0; i < length; i++) {
                Object value = Array.get(array, i);
                Object persistentValue = sanitize(value, visited);
                changed |= persistentValue != value;
                persistentArray[i] = persistentValue;
            }
            return changed ? persistentArray : array;
        } finally {
            visited.remove(array);
        }
    }
}
