package dev.langchain4j.service;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The values of method parameters annotated with @V, together with prompt templates defined by @UserMessage
 * and @SystemMessage, are used to produce a message that will be sent to the LLM.
 * Variables (placeholders), like {{xxx}} in prompt templates, are filled with the corresponding values
 * of parameters annotated with @V("xxx").
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
 * @see dev.langchain4j.model.input.PromptTemplate
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface V {

    /**
     * Name of a variable (placeholder) in a prompt template.
     */
    String value();
}
