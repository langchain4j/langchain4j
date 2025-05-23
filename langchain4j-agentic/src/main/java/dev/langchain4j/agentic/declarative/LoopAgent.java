package dev.langchain4j.agentic.declarative;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({TYPE})
public @interface LoopAgent {

    String outputName() default "";

    Subagent[] subagents();

    int maxIterations() default 10;

    String condition() default "";
}
