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
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
// todo: should we really be doing this? what about VECTOR_FLOAT16, VECTOR_INT8, VECTOR_FLOAT64, VECTOR_BINARY,
// SPARSE_VECTOR_INT8, SPARSE_VECTOR_FLOAT32 and SPARSE_VECTOR_FLOAT64?
//  => Allow annotation override
@JdbcTypeCode(SqlTypes.VECTOR_FLOAT32)
public @interface Embedding {}
