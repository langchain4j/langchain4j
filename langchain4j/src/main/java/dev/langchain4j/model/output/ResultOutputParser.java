package dev.langchain4j.model.output;

import dev.langchain4j.service.Result;
import lombok.Builder;

import java.util.Optional;
import java.util.Set;

import static dev.langchain4j.internal.Utils.setOf;

/**
 * Parser that parses the content and returns a Result object
 * that contains the parsed content and the sources.
 * @param <T> The type of the content.
 */
@Builder
public class ResultOutputParser<T> implements OutputParser<Result<T>> {
    private final Class<Result<T>> resultType;
    private final Class<T> elementType;
    private final ParserProvider parserProvider;

    @Override
    public Set<Class<?>> getSupportedTypes() {
        return setOf(resultType);
    }

    @Override
    public Result<T> parse(final OutputParsingContext context) {
        final T parsedResponse = elementType.cast(parserProvider.getOrDefault(elementType).parse(context));
        return Result.<T>builder()
                .content(parsedResponse)
                .sources(context.getSources())
                .tokenUsage(context.getTokenUsage())
                .build();
    }

    @Override
    public String formatInstructions() {
        return parserProvider.getOrDefault(elementType).formatInstructions();
    }

    @Override
    public String customFormatPrelude() {
        return parserProvider.getOrDefault(elementType).customFormatPrelude();
    }

    public static Factory factory() {
        return new Factory();
    }

    public static class Factory implements ParserFactory {
        @SuppressWarnings("unchecked")
        @Override
        public Optional<OutputParser<?>> create(final TypeInformation typeInformation, final ParserProvider parserProvider) {
            if (!Result.class.equals(typeInformation.getRawType())) {
                return Optional.empty();
            }
            if (!typeInformation.isGeneric()) {
                return Optional.empty();
            }
            return Optional.of(ResultOutputParser.builder()
                    .resultType((Class<Result<Object>>) typeInformation.getRawType())
                    .elementType((Class<Object>) typeInformation.getGenericTypes().get(0))
                    .parserProvider(parserProvider)
                    .build());
        }
    }
}
