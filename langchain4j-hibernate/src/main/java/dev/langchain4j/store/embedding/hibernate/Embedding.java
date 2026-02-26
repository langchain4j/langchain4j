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
 * the embedding vector.
 * By default, a 32-bit floating point vector is assumed.
 * To override the vector type, annotate e.g. {@code @JdbcTypeCode(SqlTypes.VECTOR_FLOAT16)}.
 *
 * @see JdbcTypeCode
 * @see SqlTypes#VECTOR_BINARY
 * @see SqlTypes#VECTOR_INT8
 * @see SqlTypes#VECTOR_FLOAT16
 * @see SqlTypes#VECTOR_FLOAT32
 * @see SqlTypes#VECTOR_FLOAT64
 * @see SqlTypes#SPARSE_VECTOR_INT8
 * @see SqlTypes#SPARSE_VECTOR_FLOAT32
 * @see SqlTypes#SPARSE_VECTOR_FLOAT64
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@JdbcTypeCode(SqlTypes.VECTOR_FLOAT32)
public @interface Embedding {}
