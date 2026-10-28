package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.Internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

@Internal
class TypeChecker {

    private static final List<Class<?>> SUPPORTED_COMPARISON_VALUE_TYPES = List.of(
            String.class, UUID.class, Boolean.class, Integer.class, Long.class, Float.class, Double.class,
            Byte.class, Short.class, BigInteger.class, BigDecimal.class);

    private static final String SUPPORTED_COMPARISON_VALUE_TYPES_HINT = supportedComparisonValueTypesHint();

    static <T> T ensureTypeIsSupported(T comparisonValue, String key) {
        if (!SUPPORTED_COMPARISON_VALUE_TYPES.contains(comparisonValue.getClass())) {
            throw illegalArgument(
                    "Unsupported type of comparisonValue with key '%s': %s. %s",
                    key,
                    comparisonValue.getClass().getName(),
                    SUPPORTED_COMPARISON_VALUE_TYPES_HINT
            );
        }
        if (isNotFinite(comparisonValue)) {
            throw illegalArgument("comparisonValue with key '%s' must be a finite number", key);
        }
        return comparisonValue;
    }

    static void ensureTypesAreSupported(Collection<?> comparisonValues, String key) {
        for (Object comparisonValue : comparisonValues) {
            ensureNotNull(comparisonValue, "comparisonValue with key '" + key + "'");
            if (!SUPPORTED_COMPARISON_VALUE_TYPES.contains(comparisonValue.getClass())) {
                throw illegalArgument(
                        "Unsupported type of a value in comparisonValues with key '%s': %s. %s",
                        key,
                        comparisonValue.getClass().getName(),
                        SUPPORTED_COMPARISON_VALUE_TYPES_HINT
                );
            }
            if (isNotFinite(comparisonValue)) {
                throw illegalArgument("comparisonValues with key '%s' must contain only finite numbers", key);
            }
        }
    }

    private static boolean isNotFinite(Object comparisonValue) {
        return comparisonValue instanceof Double doubleValue && !Double.isFinite(doubleValue)
                || comparisonValue instanceof Float floatValue && !Float.isFinite(floatValue);
    }

    private static String supportedComparisonValueTypesHint() {
        List<String> names = SUPPORTED_COMPARISON_VALUE_TYPES.stream().map(Class::getSimpleName).toList();
        return "Supported types are " + String.join(", ", names.subList(0, names.size() - 1))
                + " and " + names.get(names.size() - 1)
                + ". Convert the value to one of these types first, for example an enum with name().";
    }

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
