package dev.langchain4j;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

/**
 * Indicates that a class/constructor/method is planned to change soon.
 */
@Target({TYPE, CONSTRUCTOR, METHOD})
public @interface WillChangeSoon {

    String value();
}
