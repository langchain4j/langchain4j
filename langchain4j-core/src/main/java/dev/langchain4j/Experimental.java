package dev.langchain4j;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a class/constructor/method is experimental and might change in the future.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({TYPE, CONSTRUCTOR, METHOD, PACKAGE})
public @interface Experimental {
    /**
     * Describes why the annotated element is experimental
     *
     * @return The experimental description
     */
    String value() default "This feature is experimental and the API is subject to change";
}
