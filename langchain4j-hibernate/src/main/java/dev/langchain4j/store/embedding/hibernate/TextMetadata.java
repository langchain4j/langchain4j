package dev.langchain4j.store.embedding.hibernate;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Marks the persistent attribute of an {@code @Entity} that represents
 * the generic text metadata e.g. a {@link java.util.Map} or {@link String}
 * mapped as JSON.
 * There may only be a single text metadata attribute, which is the container
 * for all metadata that has no explicit {@code @Metadata} attribute.
 * @see Metadata
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@JdbcTypeCode(SqlTypes.JSON)
public @interface TextMetadata {}
