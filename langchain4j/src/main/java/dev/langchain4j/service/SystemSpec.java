package dev.langchain4j.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code SystemSpec} annotation defines the specifications for a system message or module within an application.
 * It allows specifying attributes such as name, description, and a customizable message template, facilitating dynamic message generation.
 * This annotation optionally supports a delimiter for joining multi-line template elements, enhancing template flexibility.
 *
 * <p>Attributes:</p>
 * <ul>
 *   <li>{@code name} - The name of the system message, which uniquely identifies it. This is a mandatory attribute.</li>
 *   <li>{@code description} - A brief description of the system message's purpose. This is a mandatory attribute.</li>
 *   <li>{@code template} - An array of strings that serves as the template for generating system messages. This is a mandatory attribute.</li>
 *   <li>{@code delimiter} - An optional attribute that specifies the string used to join template elements. The default is a newline ("\n").</li>
 * </ul>
 *
 * <p>Usage Example:</p>
 * <pre>
 * {@code
 * @SystemSpec(
 *     name = "example",
 *     description = "Manages the example operations",
 *     template = {"Step 1: Initialize", "Step 2: Execute", "Step 3: Terminate"},
 *     delimiter = ","
 * )
 * }
 * </pre>
 * This annotation is ideal for defining static or dynamic messages within the system, providing clear guidelines on the message format and content.
 * <br>
 * This annotation can only be used within the {@code RegisterSystemSpecs} annotation.
 * <p>For more information about how this annotation is used in practice, refer to:</p>
 * @see RegisterSystemSpecs
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemSpec {
    /**
     * The name of the system message, which uniquely identifies it. This is a mandatory attribute..
     *
     * @return the name.
     */
    String name();

    /**
     * A brief description of the system message's purpose. This is a mandatory attribute.
     *
     * @return the description.
     */
    String description();

    /**
     * An array of strings that serves as the template for generating system messages. This is a mandatory attribute.
     *
     * @return the template array.
     */
    String[] template();

    /**
     * Specifies the delimiter used to join elements of the {@code template}.
     * The default delimiter is a newline ("\n").
     *
     * @return the delimiter string.
     */
    String delimiter() default "\n";
}
