package dev.langchain4j.service;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Function;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The {@code RegisterSystemSpecs} annotation is designed to consolidate and apply multiple {@code SystemSpec} annotations to a single class or method.
 * It acts as a container that facilitates the configuration of various system specifications within an application, enabling complex and conditional system behaviors based on the context provided by user interactions.
 *
 * <p>Attributes:</p>
 * <ul>
 *   <li>{@code value} - Specifies an array of {@code SystemSpec} annotations, each describing the configurations and operational details of different system modules or services.</li>
 * </ul>
 *
 * <p>Usage Example:</p>
 * <pre>
 * interface Assistant {
 *
 *      {@code @RegisterSystemSpecs}({
 *          {@code @SystemSpec}(
 *              name = "system1",
 *              description = "Manages initial data processing",
 *              template = {"Initialize system1", "Process data"}
 *          ),
 *          {@code @SystemSpec}(
 *              name = "system2",
 *              description = "Handles operational start-up routines",
 *              template = {"Configure system2", "Start operations"}
 *          )
 *      })
 *     String chat(String userMessage);
 * }
 * </pre>
 * When both {@code @RegisterSystemSpecs} and {@link AiServices#systemSpecProvider(Function)} are configured,
 * {@code @RegisterSystemSpecs} takes precedence.
 * <br>
 * When both {@code @RegisterSystemSpecs} and {@code @SystemMessage} are configured,
 * {@code @SystemMessage} takes precedence.
 * <br>
 * This setup allows the application to dynamically select and execute system specifications based on the content or context of user messages.
 *
 * @see SystemSpec
 */
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface RegisterSystemSpecs {
    /**
     * Retrieves an array of {@code SystemSpec} annotations. Each annotation encapsulates specific system messages and configurations.
     * This allows for detailed and customized system responses tailored to different operational contexts within the application.
     *
     * @return An array of SystemSpec annotations detailing different system messages and their respective configurations.
     * @see SystemSpec
     */
    SystemSpec[] value();
}
