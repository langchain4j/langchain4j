package dev.langchain4j.model.input.structured;

import dev.langchain4j.internal.ValidationUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
public @interface StructuredPrompt {

    /**
     * Prompt template can be defined in one line or multiple lines.
     * If the template is defined in multiple lines, the lines will be joined with a delimiter defined below.
     */
    String[] value();

    String delimiter() default "\n";

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
    }
}
