package dev.langchain4j.model.output.structured;

import dev.langchain4j.model.output.*;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.Set;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to declare which output parser to use for a method.
 * <p>
 * The referenced {@link OutputParser} or {@link ParserFactory} must have a public no-args constructor.
 * <p>
 * A new instance of the parser or factory will be created each method the {@link Parse} annotation is on.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface Parse {
    /**
     * The parser to use.
     * @return the parser
     */
    Class<? extends OutputParser<?>> parser() default NullOutputParser.class;

    /**
     * The factory to use.
     * @return the factory
     */
    Class<? extends ParserFactory> factory() default NullParserFactory.class;

    /**
     * A parser that always returns null.
     */
    class NullOutputParser implements OutputParser<Object> {
        @Override
        public Set<Class<?>> getSupportedTypes() {
            return null;
        }

        @Override
        public Object parse(final OutputParsingContext context) {
            return null;
        }

        @Override
        public String formatInstructions() {
            return null;
        }
    }

    /**
     * A parser factory that always returns empty.
     */
    class NullParserFactory implements ParserFactory {
        @Override
        public Optional<OutputParser<?>> create(final TypeInformation typeInformation, final ParserProvider parserProvider) {
            return Optional.empty();
        }
    }
}
