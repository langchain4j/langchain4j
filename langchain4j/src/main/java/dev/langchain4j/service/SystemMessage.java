package dev.langchain4j.service;

import dev.langchain4j.exception.IllegalConfigurationException;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Function;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies either a complete system message (prompt) or a system message template to be used each time an AI service is invoked.
 * <br>
 * An example:
 * <pre>
 * interface Assistant {
 *
 *     {@code @SystemMessage}("You are a helpful assistant")
 *     String chat(String userMessage);
 * }
 * </pre>
 * The system message can contain template variables,
 * which will be resolved with values from method parameters annotated with @{@link V}.
 * <br>
 * An example:
 * <pre>
 * interface Assistant {
 *
 *     {@code @SystemMessage}("You are a {{characteristic}} assistant")
 *     String chat(@UserMessage String userMessage, @V("characteristic") String characteristic);
 * }
 * </pre>
 * When both {@code @SystemMessage} and {@link AiServices#systemMessageProvider(Function)} are configured,
 * {@code @SystemMessage} takes precedence.
 *
 * @see UserMessage
 */
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
