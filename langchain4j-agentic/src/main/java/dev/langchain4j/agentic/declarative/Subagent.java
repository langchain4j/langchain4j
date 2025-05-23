package dev.langchain4j.agentic.declarative;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({METHOD})
public @interface Subagent {

    Class<?> agentClass() default Object.class;

    String agentName() default "";

    String outputName() default "";
}
