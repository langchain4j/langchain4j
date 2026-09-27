package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.List;
import java.util.Map;

final class FreeFormValue {

    private FreeFormValue() {}

    static Object ensureValid(Object value, String name) {
        ensureNotNull(value, name);
        if (value instanceof String text) {
            return ensureNotBlank(text, name);
        }
        if (value instanceof Map<?, ?> map) {
            return copy(ensureNotEmpty(map, name));
        }
        if (value instanceof List<?> list) {
            return copy(ensureNotEmpty(list, name));
        }
        throw new IllegalArgumentException(name + " must be a String, a Map or a List, but was "
                + value.getClass().getName());
    }
}
