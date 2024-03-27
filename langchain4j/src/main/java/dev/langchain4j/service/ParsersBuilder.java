package dev.langchain4j.service;

import dev.langchain4j.model.output.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;

public class ParsersBuilder {
    private final Map<Class<?>, OutputParser<?>> parsers = new HashMap<>();

    public ParsersBuilder defaultParsers() {
        parsers.put(boolean.class, new BooleanOutputParser());
        parsers.put(Boolean.class, new BooleanOutputParser());
        parsers.put(byte.class, new ByteOutputParser());
        parsers.put(Byte.class, new ByteOutputParser());
        parsers.put(short.class, new ShortOutputParser());
        parsers.put(Short.class, new ShortOutputParser());
        parsers.put(int.class, new IntOutputParser());
        parsers.put(Integer.class, new IntOutputParser());
        parsers.put(long.class, new LongOutputParser());
        parsers.put(Long.class, new LongOutputParser());
        parsers.put(BigInteger.class, new BigIntegerOutputParser());
        parsers.put(float.class, new FloatOutputParser());
        parsers.put(Float.class, new FloatOutputParser());
        parsers.put(double.class, new DoubleOutputParser());
        parsers.put(Double.class, new DoubleOutputParser());
        parsers.put(BigDecimal.class, new BigDecimalOutputParser());
        parsers.put(Date.class, new DateOutputParser());
        parsers.put(LocalDate.class, new LocalDateOutputParser());
        parsers.put(LocalTime.class, new LocalTimeOutputParser());
        parsers.put(LocalDateTime.class, new LocalDateTimeOutputParser());
        return this;
    }

    public ParsersBuilder parser(Class<?> clazz, OutputParser<?> parser) {
        parsers.put(clazz, parser);
        return this;
    }

    public Map<Class<?>, OutputParser<?>> build() {
        return unmodifiableMap(parsers);
    }

}
