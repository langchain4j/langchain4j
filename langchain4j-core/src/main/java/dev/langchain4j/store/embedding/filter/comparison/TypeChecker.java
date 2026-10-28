package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.Internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.stream.Collectors.joining;

@Internal
class TypeChecker {

    private static final List<Class<?>> SUPPORTED_COMPARISON_VALUE_TYPES = List.of(
            String.class, UUID.class, Boolean.class, Integer.class, Long.class, Float.class, Double.class,
            Byte.class, Short.class, BigInteger.class, BigDecimal.class,
            LocalDate.class, LocalDateTime.class, LocalTime.class, OffsetDateTime.class, OffsetTime.class,
            ZonedDateTime.class, Instant.class, Year.class, YearMonth.class, MonthDay.class, Date.class);

    // matched by name, so that this class does not depend on the java.sql module
    private static final List<String> SUPPORTED_JAVA_SQL_TYPE_NAMES =
            List.of("java.sql.Date", "java.sql.Time", "java.sql.Timestamp");

    private static final String SUPPORTED_COMPARISON_VALUE_TYPES_HINT = supportedComparisonValueTypesHint();

    static <T> T ensureTypeIsSupported(T comparisonValue, String key) {
        if (!isSupportedType(comparisonValue)) {
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
            if (!isSupportedType(comparisonValue)) {
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

    private static boolean isSupportedType(Object comparisonValue) {
        return SUPPORTED_COMPARISON_VALUE_TYPES.contains(comparisonValue.getClass())
                || SUPPORTED_JAVA_SQL_TYPE_NAMES.contains(comparisonValue.getClass().getName())
                || comparisonValue instanceof Enum<?>;
    }

    private static boolean isNotFinite(Object comparisonValue) {
        return comparisonValue instanceof Double doubleValue && !Double.isFinite(doubleValue)
                || comparisonValue instanceof Float floatValue && !Float.isFinite(floatValue);
    }

    private static String supportedComparisonValueTypesHint() {
        String names = Stream.concat(
                        SUPPORTED_COMPARISON_VALUE_TYPES.stream()
                                .map(type -> type == Date.class ? type.getName() : type.getSimpleName()),
                        SUPPORTED_JAVA_SQL_TYPE_NAMES.stream())
                .collect(joining(", "));
        return "Supported types are " + names + " and enums"
                + ". Convert the value to one of these types first, for example a Character with String.valueOf().";
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
