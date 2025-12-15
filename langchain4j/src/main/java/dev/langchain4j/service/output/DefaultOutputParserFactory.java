package dev.langchain4j.service.output;

import dev.langchain4j.Internal;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Internal
class DefaultOutputParserFactory implements OutputParserFactory {

    private static final Map<Class<?>, OutputParser<?>> OUTPUT_PARSERS = new HashMap<>();

    static {
        OUTPUT_PARSERS.put(boolean.class, new BooleanOutputParser());
        OUTPUT_PARSERS.put(Boolean.class, new BooleanOutputParser());

        OUTPUT_PARSERS.put(byte.class, new ByteOutputParser());
        OUTPUT_PARSERS.put(Byte.class, new ByteOutputParser());

        OUTPUT_PARSERS.put(short.class, new ShortOutputParser());
        OUTPUT_PARSERS.put(Short.class, new ShortOutputParser());

        OUTPUT_PARSERS.put(int.class, new IntegerOutputParser());
        OUTPUT_PARSERS.put(Integer.class, new IntegerOutputParser());

        OUTPUT_PARSERS.put(long.class, new LongOutputParser());
        OUTPUT_PARSERS.put(Long.class, new LongOutputParser());

        OUTPUT_PARSERS.put(BigInteger.class, new BigIntegerOutputParser());

        OUTPUT_PARSERS.put(float.class, new FloatOutputParser());
        OUTPUT_PARSERS.put(Float.class, new FloatOutputParser());

        OUTPUT_PARSERS.put(double.class, new DoubleOutputParser());
        OUTPUT_PARSERS.put(Double.class, new DoubleOutputParser());

        OUTPUT_PARSERS.put(BigDecimal.class, new BigDecimalOutputParser());

        OUTPUT_PARSERS.put(Date.class, new DateOutputParser());
        OUTPUT_PARSERS.put(LocalDate.class, new LocalDateOutputParser());
        OUTPUT_PARSERS.put(LocalTime.class, new LocalTimeOutputParser());
        OUTPUT_PARSERS.put(LocalDateTime.class, new LocalDateTimeOutputParser());
    }

    @Override
    public OutputParser<?> get(Class<?> rawClass, Class<?> typeArgumentClass) {

        if (rawClass.isEnum()) {
            return new EnumOutputParser<>(rawClass.asSubclass(Enum.class));
        }

        if (rawClass.equals(List.class)) {
            if (typeArgumentClass.isEnum()) {
                return new EnumListOutputParser<>(typeArgumentClass.asSubclass(Enum.class));
            }

            if (typeArgumentClass.equals(String.class)) {
                return new StringListOutputParser();
            }

            return new PojoListOutputParser<>(typeArgumentClass);
        }

        if (rawClass.equals(Set.class)) {
            if (typeArgumentClass.isEnum()) {
                return new EnumSetOutputParser<>(typeArgumentClass.asSubclass(Enum.class));
            }

            if (typeArgumentClass.equals(String.class)) {
                return new StringSetOutputParser();
            }

            return new PojoSetOutputParser<>(typeArgumentClass);
        }

        OutputParser<?> outputParser = OUTPUT_PARSERS.get(rawClass);
        if (outputParser != null) {
            return outputParser;
        } else {
            return new PojoOutputParser<>(rawClass);
        }
    }
}
