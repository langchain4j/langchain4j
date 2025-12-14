package dev.langchain4j.json;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(TYPE)
public @interface Polymorphic {

    /**
     * Discriminator field name in JSON, e.g. "type".
     */
    String discriminator() default "type";
}
