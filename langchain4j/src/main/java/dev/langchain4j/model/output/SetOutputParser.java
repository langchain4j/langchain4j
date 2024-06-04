package dev.langchain4j.model.output;

import lombok.Builder;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.setOf;

/**
 * Parses sets of arbitrary types.
 * @param <T> the type of element to parse.
 */
@Builder
public class SetOutputParser<T> implements TextOutputParser<Set<T>> {
    private final Class<Set<T>> setType;
    private final TextOutputParser<?> elementParser;

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(setType);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<T> parse(final String text) {
        return (Set<T>) Arrays.stream(text.split("\n"))
                .map(elementParser::parse)
                .collect(Collectors.toSet());
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

    public static Factory factory() {
        return new Factory();
    }

    public static class Factory implements ParserFactory {
        @SuppressWarnings("unchecked")
        @Override
        public Optional<OutputParser<?>> create(final TypeInformation typeInformation, final ParserProvider parserProvider) {
            if (!Set.class.equals(typeInformation.getRawType())) {
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
            return Optional.of(SetOutputParser.builder()
                    .setType((Class<Set<Object>>) typeInformation.getRawType())
                    .elementParser((TextOutputParser<?>) outputParser)
                    .build());
        }
    }
}
