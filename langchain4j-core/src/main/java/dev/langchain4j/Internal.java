package dev.langchain4j;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

/**
 * Indicates that the annotated class is intended for internal use only within the library.
 * <p>
 * This annotation serves as a signal to library users that the annotated class is not part of the public API
 * and may change or be removed at any time without notice.
 * <p>
 * Usage of internal APIs by external code is strongly discouraged and may lead to
 * compatibility issues in future versions of the library.
 */
@Target({TYPE})
public @interface Internal {
}
