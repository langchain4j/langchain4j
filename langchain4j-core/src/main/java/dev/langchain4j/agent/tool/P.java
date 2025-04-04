package dev.langchain4j.agent.tool;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import dev.langchain4j.model.chat.request.json.CustomSchemaElement;
import dev.langchain4j.model.chat.request.json.NoneCustomSchemaElement;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Parameter of a Tool
 */
@Retention(RUNTIME)
@Target({PARAMETER})
public @interface P {

    /**
     * Description of a parameter
     * @return the description of a parameter
     */
    String value();

    /**
     * Whether the parameter is required
     * @return true if the parameter is required, false otherwise
     * Default is true.
     */
    boolean required() default true;

    Class<? extends CustomSchemaElement> jsonSchema() default NoneCustomSchemaElement.class;
}
