package dev.langchain4j.agent.tool;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tool parameter handler for `Double` in kinds of parameter type(e.g. int/long/short/byte,etc)
 */
enum ToolDoubleParameterHandlers {
    INSTANT(Instant.class, Double::longValue, Instant::ofEpochMilli),
    LOCAL_DATE_TIME(LocalDateTime.class, INSTANT::<Instant>apply, it -> LocalDateTime.ofInstant(it, ZoneId.systemDefault())),
    LOCAL_DATE(LocalDate.class, LOCAL_DATE_TIME::<LocalDateTime>apply, LocalDateTime::toLocalDate),
    LOCAL_TIME(LocalTime.class, LOCAL_DATE_TIME::<LocalDateTime>apply, LocalDateTime::toLocalTime),

    BIG_DECIMAL(BigDecimal.class, BigDecimal::valueOf),
    BIG_INTEGER(BigInteger.class, BIG_DECIMAL::<BigDecimal>apply, BigDecimal::toBigInteger),

    STR(String.class, String::valueOf),
    DOUBLE(Double.class, ToolParameterHandler.identity()),
    DOUBLE_PRIMITIVE(double.class, DOUBLE::apply),
    FLOAT(Float.class, Double::floatValue) {
        @Override
        protected boolean testDoubleValue(Double value) {
            if (value < -Float.MAX_VALUE || value > Float.MAX_VALUE) {
                throw new IllegalArgumentException("Double value " + value + " is out of range for the float type");
            }
            return true;
        }
    },
    FLOAT_PRIMITIVE(float.class, FLOAT::apply),
    LONG(Long.class, Double::longValue) {
        @Override
        protected boolean testDoubleValue(Double value) {
            if (value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
                throw new IllegalArgumentException("Double value " + value + " is out of range for the long type");
            }
            return true;
        }
    },
    LONG_PRIMITIVE(long.class, LONG::apply),
    INTEGER(Integer.class, Double::intValue) {
        @Override
        protected boolean testDoubleValue(Double value) {
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Double value " + value + " is out of range for the integer type");
            }
            return true;
        }
    },
    INTEGER_PRIMITIVE(int.class, INTEGER::apply),
    SHORT(Short.class, Double::shortValue) {
        @Override
        protected boolean testDoubleValue(Double value) {
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Double value " + value + " is out of range for the short type");
            }
            return true;
        }
    },
    SHORT_PRIMITIVE(short.class, SHORT::apply),
    BYTE(Byte.class, Double::byteValue) {
        @Override
        protected boolean testDoubleValue(Double value) {
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("Double value " + value + " is out of range for the byte type");
            }
            return true;
        }
    },
    BYTE_PRIMITIVE(byte.class, BYTE::apply);

    final Class<?> parameterType;
    final ToolParameterHandler<Double, ?> func;

    <R> ToolDoubleParameterHandlers(Class<R> parameterType, ToolParameterHandler<Double, R> func) {
        this.parameterType = parameterType;
        this.func = func;
    }

    <U, R> ToolDoubleParameterHandlers(Class<R> parameterType, ToolParameterHandler<Double, U> compose, ToolParameterHandler<U, R> func) {
        this.parameterType = parameterType;
        this.func = func.compose(compose);
    }

    protected boolean testDoubleValue(Double value) {
        return true;
    }

    /**
     * Only internal processed, make sure `cast` correct type in compile stage strictly.
     */
    @SuppressWarnings("unchecked")
    <R> R apply(Double value) {
        if (testDoubleValue(value)) {
            return (R) func.apply(value);
        }

        return null;
    }

    static final Map<Class<?>, Function<Double, ?>> NUMBER_PARAMETER_HANDLER_MAP = Arrays.stream(ToolDoubleParameterHandlers.values())
            .collect(Collectors.toMap(it -> it.parameterType, it -> it::apply));

    public static Object handleDouble(Class<?> parameterType, Double value) {
        if (!NUMBER_PARAMETER_HANDLER_MAP.containsKey(parameterType)) {
            throw new IllegalArgumentException("Double value does not support the " + parameterType + "parameter type.");
        }

        return NUMBER_PARAMETER_HANDLER_MAP.get(parameterType).apply(value);
    }
}
