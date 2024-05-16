package dev.langchain4j.model.output;

import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default parsers.
 */
@RequiredArgsConstructor
public enum StandardOutputParsers {
    RESPONSE(new ResponseOutputParser()),
    MESSAGE(new AiMessageOutputParser()),
    TOKEN_STREAM(new TokenStreamOutputParser()),
    BOOLEAN(new BooleanOutputParser()),
    BYTE(new ByteOutputParser()),
    SHORT(new ShortOutputParser()),
    INT(new IntOutputParser()),
    LONG(new LongOutputParser()),
    BIG_INTEGER(new BigIntegerOutputParser()),
    FLOAT(new FloatOutputParser()),
    DOUBLE(new DoubleOutputParser()),
    BIG_DECIMAL(new BigDecimalOutputParser()),
    DATE(new DateOutputParser()),
    LOCAL_DATE(new LocalDateOutputParser()),
    LOCAL_TIME(new LocalTimeOutputParser()),
    LOCAL_DATE_TIME(new LocalDateTimeOutputParser()),
    STRING(new StringOutputParser());

    private final OutputParser<?> parser;

    public static List<OutputParser<?>> asList() {
        return Arrays.stream(values())
                .map(v -> v.parser)
                .collect(Collectors.toList());
    }

    public static Map<Class<?>, OutputParser<?>> asMap() {
        final Map<Class<?>, OutputParser<?>> map = new HashMap<>();
        for (StandardOutputParsers outputParser : values()) {
            outputParser.parser.getSupportedTypes().forEach(clazz -> map.put(clazz, outputParser.parser));
        }
        return map;
    }
}
