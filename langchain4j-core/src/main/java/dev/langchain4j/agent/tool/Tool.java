package dev.langchain4j.agent.tool;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({METHOD})
public @interface Tool {

    /**
     * Name of the tool. If not provided, method name will be used.
     */
    String name() default "";

    /**
     * Description of the tool. LLM should understand what this tool is for and when it should be used.
     */
    String[] value() default "";
}
