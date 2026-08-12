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
 * the unmapped metadata e.g. a {@link java.util.Map} or {@link String}
 * mapped as JSON.
 * There may only be a single unmapped metadata attribute, which is the container
 * for all metadata that has no explicit {@code @MetadataAttribute} attribute.
 * @see MetadataAttribute
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@JdbcTypeCode(SqlTypes.JSON)
public @interface UnmappedMetadata {}
