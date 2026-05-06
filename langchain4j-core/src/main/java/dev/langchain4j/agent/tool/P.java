package dev.langchain4j.agent.tool;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for parameters of a {@link Tool}-annotated method.
 *
 * <h2>Description</h2>
 * {@link #value()} and {@link #description()} are aliases for the same thing: the parameter's description
 * that the LLM will see. Use one or the other, but not both at the same time.
 * <p>When only a description is needed, {@code value} can be used as a shorthand:
 * <pre>{@code
 * @Tool
 * void getWeather(@P("The city name") String city) { ... }
 * }</pre>
 * <p>When both a name and a description are needed, use named attributes:
 * <pre>{@code
 * @Tool
 * void getWeather(@P(name = "city", description = "The city name") String city) { ... }
 * }</pre>
 *
 * <h2>Name</h2>
 * The {@link #name()} attribute overrides the parameter name that the LLM will see.
 * This is useful in two cases:
 * <ol>
 *   <li><b>Missing {@code -parameters} javac option.</b>
 *       Without it (common when not using frameworks like Quarkus or Spring, which enable it by default),
 *       Java reflection returns generic names such as {@code arg0}, {@code arg1}, etc.
 *       The semantic meaning of the parameter is lost, which may confuse the LLM.
 *       Setting {@code name} restores a meaningful name.</li>
 *   <li><b>Custom name for the LLM.</b>
 *       When you want the LLM to see a different parameter name than the one the developer uses in the source code
 *       (for example, to match a specific API contract or to provide a more descriptive name).</li>
 * </ol>
 */
@Retention(RUNTIME)
@Target({PARAMETER})
public @interface P {

    /**
     * Name of the parameter as seen by the LLM.
     * <p>If not specified, the actual method parameter name is used (requires the {@code -parameters} javac option;
     * otherwise the name defaults to {@code arg0}, {@code arg1}, etc.).
     * <p>Setting this is useful when:
     * <ul>
     *   <li>The {@code -parameters} javac option is not enabled and you want to avoid
     *       generic {@code arg0}/{@code arg1} names.
     *       Note that frameworks like Quarkus and Spring enable {@code -parameters} by default,
     *       so you typically do not need to set {@code name} when using those frameworks.</li>
     *   <li>You want the LLM to see a different name than the one in the source code.</li>
     * </ul>
     *
     * @return the name of the parameter
     */
    String name() default "";

    /**
     * Description of the parameter. This is an alias for {@link #value()}.
     * Use either {@code value} or {@code description}, but not both.
     *
     * @return the description of the parameter
     */
    String description() default "";

    /**
     * Description of the parameter. This is an alias for {@link #description()}.
     * Use either {@code value} or {@code description}, but not both.
     * <p>Convenient for the shorthand form: {@code @P("description here")}.
     *
     * @return the description of the parameter
     */
    String value() default "";

    /**
     * Whether the parameter is required.
     * Default is {@code true}.
     *
     * @return {@code true} if the parameter is required, {@code false} otherwise
     */
    boolean required() default true;
}
