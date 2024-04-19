package dev.langchain4j.service;

import dev.langchain4j.model.input.PromptTemplate;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Function;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * When a parameter of a method in an AI Service is annotated with {@code @V},
 * it becomes a prompt template variable. Its value will be injected into prompt templates defined
 * via @{@link UserMessage}, @{@link SystemMessage} and {@link AiServices#systemMessageProvider(Function)}.
 * <p>
 * Example:
 * <pre>
 * {@code @UserMessage("Hello, my name is {{name}}. I am {{age}} years old.")}
 * String chat(@V("name") String name, @V("age") int age);
 * </pre>
 * <p>
 * This annotation is necessary only when the "-parameters" option is *not* enabled during Java compilation.
 * If the "-parameters" option is enabled, parameter names can directly serve as identifiers, eliminating
 * the need to define a value of @V annotation.
 * Example:
 * <pre>
 * {@code @UserMessage("Hello, my name is {{name}}. I am {{age}} years old.")}
 * String chat(@V String name, @V int age);
 * </pre>
 * <p>
 * When using Spring Boot, defining the value of this annotation is not required.
 *
 * @see UserMessage
 * @see SystemMessage
 * @see PromptTemplate
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface V {

    /**
     * Name of a variable (placeholder) in a prompt template.
     */
    String value();
}
