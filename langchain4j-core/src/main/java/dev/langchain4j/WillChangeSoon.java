package dev.langchain4j;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Indicates that a class/constructor/method is planned to change soon.
 */
@SuppressWarnings("unused")
@Target({TYPE, CONSTRUCTOR, METHOD})
public @interface WillChangeSoon {
    /**
     * The reason for the change.
     * @return the reason for the change.
     */
    String value();
}
