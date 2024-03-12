package dev.langchain4j;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Indicates that a class/constructor/method is experimental and might change in the future.
 */
@Target({TYPE, CONSTRUCTOR, METHOD})
public @interface Experimental {
}
