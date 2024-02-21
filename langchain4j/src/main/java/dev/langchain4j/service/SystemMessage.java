package dev.langchain4j.service;

import dev.langchain4j.exception.IllegalConfigurationException;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface SystemMessage {

    /**
     * Prompt template can be defined in one line or multiple lines.
     * If the template is defined in multiple lines, the lines will be joined with a delimiter defined below.
     */
    String[] value() default "";

    String delimiter() default "\n";

    /**
     * The resource from which to read the prompt template.
     * If no resource is specified, the prompt template is taken from {@link #value()}.
     * If the resource is not found, an {@link IllegalConfigurationException} is thrown.
     * <p>
     * The resource will be read by calling {@link Class#getResourceAsStream(String)}
     * on the AI Service class (interface).
     */
    String fromResource() default "";
}
