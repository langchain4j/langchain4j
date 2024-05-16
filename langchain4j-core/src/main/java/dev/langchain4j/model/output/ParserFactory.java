package dev.langchain4j.model.output;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Factory to create {@link OutputParser} instances for a given class.
 */
public interface ParserFactory {
    /**
     * Create a parser for the given class, or return empty if no parser is available.
     *
     * @param typeInformation the type information
     * @param parserProvider the parser provider
     * @return the parser
     */
    Optional<OutputParser<?>> create(final TypeInformation typeInformation, final ParserProvider parserProvider);

    default Optional<OutputParser<?>> create(final Class<?> clazz, final ParserProvider parserProvider) {
        return create(TypeInformation.of(clazz), parserProvider);
    }

    default Optional<OutputParser<?>> create(final Method method, final ParserProvider parserProvider) {
        return create(TypeInformation.of(method), parserProvider);
    }
}
