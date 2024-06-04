package dev.langchain4j.model.output;

import lombok.Builder;

import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.setOf;

/**
 * Parses lists of arbitrary types.
 * @param <T> the type of element to parse.
 */
@Builder
public class ListOutputParser<T> implements TextOutputParser<List<T>> {
    private final Class<List<T>> listType;
    private final TextOutputParser<?> elementParser;

    @SuppressWarnings("unchecked")
    @Override
    public List<T> parse(final String text) {
        return (List<T>) Arrays.stream(text.split("\n"))
                .map(elementParser::parse)
                .collect(Collectors.toList());
    }

    @Override
    public String customFormatPrelude() {
        return elementParser.customFormatPrelude();
    }

    @Override
    public String formatInstructions() {
        return elementParser.formatInstructions();
    }

    @Override
    public String customFormatPostlude() {
        return "\nYou must put every item on a separate line.";
    }

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(listType);
    }

    public static Factory factory() {
        return new Factory();
    }

    public static class Factory implements ParserFactory {
        @SuppressWarnings("unchecked")
        @Override
        public Optional<OutputParser<?>> create(final TypeInformation typeInformation, final ParserProvider parserProvider) {
            if (!List.class.equals(typeInformation.getRawType())) {
                return Optional.empty();
            }
            if (!typeInformation.isGeneric()) {
                return Optional.empty();
            }
            if (typeInformation.getGenericTypes().size() != 1) {
                return Optional.empty();
            }
            final Class<?> genericType = typeInformation.getGenericTypes().get(0);
            final OutputParser<?> outputParser = parserProvider.getOrDefault(genericType);
            if (!(outputParser instanceof TextOutputParser)) {
                return Optional.empty();
            }
            return Optional.of(ListOutputParser.builder()
                    .listType((Class<List<Object>>) typeInformation.getRawType())
                    .elementParser((TextOutputParser<?>) outputParser)
                    .build());
        }
    }
}
