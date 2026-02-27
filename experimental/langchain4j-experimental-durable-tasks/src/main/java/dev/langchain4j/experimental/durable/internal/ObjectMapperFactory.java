package dev.langchain4j.experimental.durable.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.Internal;

/**
 * Shared factory for creating pre-configured {@link ObjectMapper} instances
 * used throughout the durable tasks module.
 *
 * <p>All mappers register the {@link JavaTimeModule} and disable
 * {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} so that
 * {@link java.time.Instant} values are serialized as ISO-8601 strings.
 */
@Internal
public final class ObjectMapperFactory {

    private ObjectMapperFactory() {}

    /**
     * Creates a new {@link ObjectMapper} with standard configuration.
     *
     * @return a configured ObjectMapper
     */
    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Creates a new {@link ObjectMapper} with pretty-printing enabled.
     *
     * @return a configured ObjectMapper with indented output
     */
    public static ObjectMapper createPrettyPrinting() {
        ObjectMapper mapper = create();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}
