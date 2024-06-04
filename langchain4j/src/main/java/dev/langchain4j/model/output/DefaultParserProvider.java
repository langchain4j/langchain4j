package dev.langchain4j.model.output;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.With;

import java.util.*;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;

/**
 * Default implementation of the parser provider.
 */
@With
@Builder(toBuilder = true)
public class DefaultParserProvider implements ParserProvider {
    /**
     * Standard parsers (e.g. int, bool, LocalDate, etc).
     */
    @Singular
    private final Map<Class<?>, OutputParser<?>> parsers;
    /**
     * Factories for creating parsers for custom types (e.g. generic lists, enums, etc).
     */
    @Singular
    private final List<ParserFactory> factories;
    /**
     * If no parser is found from the parsers, and no parser could be created from a factory,
     * then the default factory will always be used.
     */
    @NonNull
    private final ParserFactory defaultFactory;

    /**
     * Create a new instance of the default parser provider with the standard parsers and factories.
     * <p>
     * Uses all the parsers listed in {@link StandardOutputParsers}.
     * <p>
     * In addition, it supports generic {@link List} and {@link Set} types, as well as enums.
     * <p>
     * If no appropriate parser is found, the {@link dev.langchain4j.model.output.JsonOutputParser} will be used.
     *
     * @return a new instance of the default parser provider
     */
    public static DefaultParserProvider create() {
        return standardBuilder().build();
    }

    /**
     * Create a new builder with the standard parsers and factories.
     * @return the builder
     */
    public static DefaultParserProviderBuilder standardBuilder() {
        return builder()
                .parsers(StandardOutputParsers.asMap())
                .factory(ParseAnnotationParserFactory.create())
                .factory(SetOutputParser.factory())
                .factory(ListOutputParser.factory())
                .factory(EnumOutputParser.factory())
                .factory(ResultOutputParser.factory())
                .defaultFactory(JsonOutputParser.factory());
    }

    /**
     * Get the parser for the given type information.
     * <p>
     * First, all factories are checked to see if they can create a parser for the type.
     * If no parser is found, then the standard parsers are checked.
     * If no parser is found, then the default factory is used.
     *
     * @param typeInformation the type information
     * @return the parser, or empty if no parser is available
     */
    @Override
    public Optional<OutputParser<?>> get(final TypeInformation typeInformation) {
        for (final ParserFactory factory : factories) {
            final Optional<OutputParser<?>> outputParser = factory.create(typeInformation, this);
            if (outputParser.isPresent()) {
                return outputParser;
            }
        }
        if (parsers.containsKey(typeInformation.getRawType())) {
            return Optional.of(parsers.get(typeInformation.getRawType()));
        }
        return Optional.empty();
    }

    /**
     * Get the default parser for the given type information.
     * @param typeInformation the type information
     * @return the parser
     */
    @Override
    public OutputParser<?> getDefaultParser(final TypeInformation typeInformation) {
        return defaultFactory.create(typeInformation, this)
                .orElseThrow(() -> illegalConfiguration("No default parser found for type=%s", typeInformation.getRawType()));
    }

    @Override
    public ParserProvider withParser(final OutputParser<?> parser) {
        return toBuilder()
                .addParser(parser)
                .build();
    }

    @Override
    public ParserProvider withFactory(final ParserFactory factory) {
        return toBuilder()
                .factory(factory)
                .build();
    }

    // augment the builder to support adding parsers
    public static class DefaultParserProviderBuilder {
        public DefaultParserProviderBuilder addParser(final OutputParser<?> parser) {
            parser.getSupportedTypes().forEach(clazz -> parser(clazz, parser));
            return this;
        }

        public DefaultParserProviderBuilder addParsers(final Collection<OutputParser<?>> parsers) {
            parsers.forEach(this::addParser);
            return this;
        }
    }
}
