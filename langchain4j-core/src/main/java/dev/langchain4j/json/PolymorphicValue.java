package dev.langchain4j.json;

import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(TYPE)
public @interface PolymorphicValue {

    /**
     * Discriminator value for this subtype, e.g. "text", "image".
     */
    String value();
}
