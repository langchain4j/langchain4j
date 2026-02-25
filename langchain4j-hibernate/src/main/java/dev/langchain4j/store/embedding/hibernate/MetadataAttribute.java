package dev.langchain4j.store.embedding.hibernate;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a persistent attribute of an {@code @Entity} that represents
 * a metadata attribute.
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface MetadataAttribute {}
