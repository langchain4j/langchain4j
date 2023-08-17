package dev.langchain4j.service;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The value of a method parameter annotated with @UserId will be used to find the chat memory belonging to that user.
 * A parameter annotated with @UserId can be of any type, provided it has properly implemented equals() and hashCode() methods.
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface UserId {

}
