package dev.langchain4j.model.input.structured;

import dev.langchain4j.internal.ValidationUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Represents a structured prompt.
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface StructuredPrompt {

    /**
     * Prompt template can be defined in one line or multiple lines.
     * If the template is defined in multiple lines, the lines will be joined with a delimiter defined below.
     * @return the prompt template lines.
     */
    String[] value();

    /**
     * The delimiter to join the lines of the prompt template.
     * @return the delimiter.
     */
    String delimiter() default "\n";

    /**
     * Utility class for {@link StructuredPrompt}.
     */
    class Util {
        private Util() {}

        /**
         * Validates that the given object is annotated with {@link StructuredPrompt}.
         * @param structuredPrompt the object to validate.
         * @return the annotation.
         */
        public static StructuredPrompt validateStructuredPrompt(Object structuredPrompt) {
            ValidationUtils.ensureNotNull(structuredPrompt, "structuredPrompt");

            Class<?> cls = structuredPrompt.getClass();

            return ValidationUtils.ensureNotNull(
                    cls.getAnnotation(StructuredPrompt.class),
                    "%s should be annotated with @StructuredPrompt to be used as a structured prompt",
                    cls.getName());
        }

        /**
         * Joins the lines of the prompt template.
         * @param structuredPrompt the structured prompt.
         * @return the joined prompt template.
         */
        public static String join(StructuredPrompt structuredPrompt) {
            return String.join(structuredPrompt.delimiter(), structuredPrompt.value());
        }
    }
}
