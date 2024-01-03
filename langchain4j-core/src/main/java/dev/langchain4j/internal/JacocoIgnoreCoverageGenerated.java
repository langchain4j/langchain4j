package dev.langchain4j.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods where JaCoCo coverage should be ignored.
 *
 * <p>JaCoCo coverage is ignored for methods annotated with any annotation
 * that has a name including "Generated".
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JacocoIgnoreCoverageGenerated {
}
