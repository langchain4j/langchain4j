package dev.langchain4j.service;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The value of a method parameter annotated with @UserName will be injected into the field 'name' of a UserMessage.
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface UserName {

}
