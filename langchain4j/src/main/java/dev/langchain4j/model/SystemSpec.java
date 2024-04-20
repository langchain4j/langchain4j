package dev.langchain4j.model;

import lombok.Builder;
import lombok.Getter;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * The {@code SystemSpec} class encapsulates the specifications of a system message.
 * It holds essential details that define the system's operational blueprint, including its name,
 * description, and a structured message template. This class is designed to be immutable,
 * ensuring that once an instance is created, its state cannot be altered.
 *
 * <p>Attributes:</p>
 * <ul>
 *   <li>{@code name} - Represents the name of the system message, essential for identification.</li>
 *   <li>{@code description} - Provides a brief description of what the system message is designed to do.</li>
 *   <li>{@code template} - An array of strings that outlines the template for system messages.</li>
 *   <li>{@code delimiter} - A string used to separate elements in the template, with a default value of newline ("\n").</li>
 * </ul>
 *
 * <p>Instances of this class are created using a builder pattern, allowing for a flexible and clear construction process.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * SystemSpec spec = SystemSpec.builder()
 *     .name("ExampleSystem")
 *     .description("This system handles data processing.")
 *     .template(new String[]{"Initiate", "Process", "Terminate"})
 *     .delimiter(",")
 *     .build();
 * </pre>
 *
 */
@Getter
public class SystemSpec {
    private final String name;
    private final String description;
    private final String[] template;
    private final String delimiter;


    /**
     * Constructs an instance of {@code SystemSpec} using the provided parameters.
     * This constructor enforces non-null values for name, description, and template through the {@code ensureNotNull} method.
     * If the {@code delimiter} is not provided, it defaults to a newline ("\n").
     *
     * @param name the name of the system, must not be null.
     * @param description a brief description of the system, must not be null.
     * @param template an array representing the structured message or operation template, must not be null.
     * @param delimiter the delimiter used to separate template elements, defaults to newline if null.
     * @throws NullPointerException if name, description, or template is null.
     */
    @Builder
    SystemSpec(String name, String description, String[] template, String delimiter) {
        this.name = ensureNotNull(name, "name");
        this.description = ensureNotNull(description, "description");
        this.template = ensureNotNull(template, "template");
        this.delimiter = delimiter != null ? delimiter : "\n";
    }
}
