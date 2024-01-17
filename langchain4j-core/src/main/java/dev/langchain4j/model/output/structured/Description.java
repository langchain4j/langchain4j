package dev.langchain4j.model.output.structured;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to attach a description to a class field.
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface Description {

    /**
     * The description can be defined in one line or multiple lines.
     * If the description is defined in multiple lines, the lines will be joined with a space (" ") automatically.
     * @return The description.
     */
    String[] value();
}