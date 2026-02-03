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
 * the text for which an embedding vector is created.
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
public @interface EmbeddingText {}
