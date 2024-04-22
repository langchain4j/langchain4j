package dev.langchain4j.service;

import dev.langchain4j.exception.IllegalConfigurationException;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies either a complete user message or a user message template to be used each time an AI service is invoked.
 * The user message can contain template variables,
 * which will be resolved with values from method parameters annotated with @{@link V}.
 * <br>
 * An example:
 * <pre>
 * interface Assistant {
 *
 *     {@code @UserMessage}("Say hello to {{name}}")
 *     String greet(@V("name") String name);
 * }
 * </pre>
 * {@code @UserMessage} can also be used with method parameters:
 * <pre>
 * interface Assistant {
 *
 *     {@code @SystemMessage}("You are a {{characteristic}} assistant")
 *     String chat(@UserMessage String userMessage, @V("characteristic") String characteristic);
 * }
 * </pre>
 * In this case {@code String userMessage} can contain unresolved template variables (e.g. "{{characteristic}}"),
 * which will be resolved using the values of method parameters annotated with @{@link V}.
 *
 * @see SystemMessage
 */
@Retention(RUNTIME)
@Target({METHOD, PARAMETER})
public @interface UserMessage {

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
