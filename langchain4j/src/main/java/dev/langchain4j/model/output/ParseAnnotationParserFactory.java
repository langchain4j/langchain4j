package dev.langchain4j.model.output;

import dev.langchain4j.model.output.structured.Parse;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static dev.langchain4j.exception.IllegalConfigurationException.illegalConfiguration;

/**
 * Factory for creating a parser based on the presence of a {@link Parse} annotation.
 */
@RequiredArgsConstructor(staticName = "create")
public class ParseAnnotationParserFactory implements ParserFactory {
    @Override
    public Optional<OutputParser<?>> create(final TypeInformation typeInformation, final ParserProvider parserProvider) {
        final Optional<Parse> parseAnnotation = typeInformation.getAnnotations().stream()
                .filter(annotation -> annotation instanceof Parse)
                .map(annotation -> (Parse) annotation)
                .findFirst();
        if (!parseAnnotation.isPresent()) {
            return Optional.empty();
        }
        final Parse annotation = parseAnnotation.get();
        try {
            if (annotation.parser() != Parse.NullOutputParser.class) {
                return Optional.of(annotation.parser().getConstructor().newInstance());
            }
        } catch (Exception e) {
            throw illegalConfiguration("Failed to instantiate parser=%s for type=%s", e, annotation.parser().getName(), typeInformation);
        }
        try {
            if (annotation.factory() != Parse.NullParserFactory.class) {
                final ParserFactory parserFactory = annotation.factory().getConstructor().newInstance();
                final Optional<OutputParser<?>> outputParser = parserFactory.create(typeInformation, parserProvider);
                if (outputParser.isPresent()) {
                    return outputParser;
                }
            }
        } catch (Exception e) {
            throw illegalConfiguration("Failed to instantiate parser using factory=%s for type=%s", e, annotation.factory().getName(), typeInformation);
        }
        return Optional.empty();
    }
}
