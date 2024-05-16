package dev.langchain4j.model.output;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;


/**
 * Provides parsers for output types.
 */
public interface ParserProvider {
    /**
     * Get the parser for the given class and generic type.
     * @param typeInformation the type information
     * @return the parser, or empty if no parser is available
     */
    Optional<OutputParser<?>> get(final TypeInformation typeInformation);

    OutputParser<?> getDefaultParser(final TypeInformation typeInformation);

    default OutputParser<?> getOrDefault(final TypeInformation typeInformation) {
        return get(typeInformation).orElseGet(() -> getDefaultParser(typeInformation));
    }

    /**
     * Get the parser for the given method.
     * @param method the method
     * @return the parser
     */
    default Optional<OutputParser<?>> get(final Method method) {
        return get(TypeInformation.of(method));
    }

    default OutputParser<?> getOrDefault(final Method method) {
        final TypeInformation typeInformation = TypeInformation.of(method);
        return get(typeInformation).orElseGet(() -> getDefaultParser(typeInformation));
    }

    /**
     * Get the parser for the given class.
     * @param clazz the class
     * @return the parser
     */
    default Optional<OutputParser<?>> get(final Class<?> clazz) {
        return get(TypeInformation.of(clazz));
    }

    default OutputParser<?> getOrDefault(final Class<?> clazz) {
        final TypeInformation typeInformation = TypeInformation.of(clazz);
        return get(typeInformation).orElseGet(() -> getDefaultParser(typeInformation));
    }

    /**
     * Return a new provider with the given parser.
     * @param parser the parser
     * @return the new provider
     */
    default ParserProvider withParser(final OutputParser<?> parser) {
        throw new UnsupportedOperationException("Cannot add parser=" + parser + " to provider=" + this);
    }

    /**
     * Return a new provider with the given factory.
     * @param factory the factory
     * @return the new provider
     */
    default ParserProvider withFactory(final ParserFactory factory) {
        throw new UnsupportedOperationException("Cannot add factory=" + factory + " to provider=" + this);
    }

    /**
     * Return a new provider with the given parsers.
     * @param parsers the parsers
     * @return the new provider
     */
    default ParserProvider withParsers(final Collection<OutputParser<?>> parsers) {
        ParserProvider provider = this;
        for (OutputParser<?> parser : parsers) {
            provider = provider.withParser(parser);
        }
        return provider;
    }

    /**
     * Return a new provider with the given factories.
     * @param factories the factories
     * @return the new provider
     */
    default ParserProvider withFactories(final Collection<ParserFactory> factories) {
        ParserProvider provider = this;
        for (ParserFactory factory : factories) {
            provider = provider.withFactory(factory);
        }
        return provider;
    }
}
