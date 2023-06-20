package dev.langchain4j.model.input.structured;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
public @interface StructuredPrompt {

    /**
     * Prompt template can be defined in one line or multiple lines.
     * If the template is defined in multiple lines, the lines will be joined with a newline ("\n") automatically.
     */
    String[] value();
}
