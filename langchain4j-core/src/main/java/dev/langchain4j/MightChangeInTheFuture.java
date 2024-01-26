package dev.langchain4j;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Indicates that a class/constructor/method is experimental and might change in the future.
 */
@SuppressWarnings("unused")
@Target({TYPE, CONSTRUCTOR, METHOD})
public @interface MightChangeInTheFuture {

    /**
     * A description of the change.
     * @return a description of the change.
     */
    String value();
}
