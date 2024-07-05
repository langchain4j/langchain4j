package dev.langchain4j.store.embedding.filter.comparison;

import java.util.UUID;

import static dev.langchain4j.internal.Exceptions.illegalArgument;

class TypeChecker {

    static void ensureTypesAreCompatible(Object actualValue, Object comparisonValue, String key) {
        if (actualValue instanceof Number && comparisonValue instanceof Number) {
            return;
        }

        if (actualValue instanceof String && comparisonValue instanceof UUID) {
            return;
        }

        if (actualValue.getClass() != comparisonValue.getClass()) {
            throw illegalArgument(
                    "Type mismatch: actual value of metadata key \"%s\" (%s) has type %s, " +
                            "while comparison value (%s) has type %s",
                    key,
                    actualValue,
                    actualValue.getClass().getName(),
                    comparisonValue,
                    comparisonValue.getClass().getName()
            );
        }
    }
}
