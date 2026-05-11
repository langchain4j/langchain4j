package dev.langchain4j.agent.tool;

import dev.langchain4j.exception.ToolArgumentsException;

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
     * <p>
     * The {@code required} flag controls the JSON schema sent to the LLM: required parameters are
     * listed in the schema's {@code required} array. The LLM is expected to honour this, but in
     * practice it can disregard the schema and omit an argument anyway.
     * <p>
     * <b>1.x behaviour when a required argument is missing:</b>
     * <ul>
     *   <li><b>Primitive parameters</b> ({@code int}, {@code long}, {@code boolean}, …) — detected
     *       and surfaced as a {@link ToolArgumentsException}.</li>
     *   <li><b>Object parameters</b> — not validated; {@code null} is passed to the
     *       {@link Tool}-annotated method, even though the schema marked the parameter as required.</li>
     * </ul>
     * This asymmetry will be removed in LangChain4j 2.0, where all required arguments will be validated uniformly.
     *
     * @return {@code true} if the parameter is required, {@code false} otherwise
     */
    boolean required() default true;
}
