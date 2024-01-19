package dev.langchain4j.service;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
	 * The resource to read the prompt template from. If the resource is not found,
	 * an IllegalConfigurationException is thrown. If no resource is specified we
	 * will fall-back to the value of {@link #value()}.
	 *
	 * The resource will be read by calling {@code getResourceAsStream(resource)} on the class```
	 * containing the method annotated with {@code @UserMessage}.
	 */
    String fromResource() default "";
}
