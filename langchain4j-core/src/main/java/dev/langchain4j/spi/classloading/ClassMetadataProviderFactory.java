package dev.langchain4j.spi.classloading;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * A factory interface for providing access to class metadata. Intended to be implemented by downstream frameworks.
 * <p>
 *     {@code dev.langchain4j.classloading.ReflectionBasedClassMetadataProviderFactory}
 *     provides an implementation that uses reflection, which is probably fine in most cases, but this provides hooks for
 *     other frameworks (like Quarkus) which don't use reflection to provide class metadata.
 * </p>
 * <p>
 *     The method keys this factory returns from {@link #getNonStaticMethodsOnClass(Class)} identify methods wherever
 *     the caller needs to refer to a method of a class it scanned. Code that is handed a
 *     {@link java.lang.reflect.Method} instead (for example the method being invoked) uses
 *     {@link #resolveMethodKey(Object)} to find the key that corresponds to it, which matters for a factory whose keys
 *     are not {@link java.lang.reflect.Method}s.
 * </p>
 *
 * @param <MethodKey> The type of the method key, representing a unique identifier for methods. Can be whatever it needs to be.
 */
public interface ClassMetadataProviderFactory<MethodKey> {
    /**
     * Retrieves an annotation of the specified type from the given method.
     *
     * @param <T> The type of the annotation to locate, which must extend {@link Annotation}.
     * @param method The method from which the annotation is to be retrieved.
     * @param annotationClass The class object corresponding to the annotation type to find.
     * @return An {@code Optional} containing the located annotation, or an empty {@code Optional} if the annotation
     *         is not present on the specified method.
     */
    <T extends Annotation> Optional<T> getAnnotation(MethodKey method, Class<T> annotationClass);

    /**
     * Retrieves an annotation of the specified type from the given class.
     *
     * @param <T> The type of the annotation to locate, which must extend {@link Annotation}.
     * @param clazz The class from which the annotation is to be retrieved.
     * @param annotationClass The class object corresponding to the annotation type to find.
     * @return An {@code Optional} containing the located annotation, or an empty {@code Optional} if the annotation
     *         is not present on the specified class.
     */
    <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz, Class<T> annotationClass);

    /**
     * Retrieves an iterable containing method keys for all non-static methods defined in the specified class.
     *
     * @param clazz The class from which to retrieve methods.
     * @return An iterable of method keys corresponding to the methods of the specified class.
     */
    Iterable<MethodKey> getNonStaticMethodsOnClass(Class<?> clazz);

    /**
     * Resolves the method key for a method that is identified in another way than by this factory's keys, usually by
     * a {@link java.lang.reflect.Method}.
     * <p>
     *     The keys returned by {@link #getNonStaticMethodsOnClass(Class)} are what this factory uses to identify
     *     methods. A caller that only has the {@link java.lang.reflect.Method} (for example, the method being invoked)
     *     calls this method to find the corresponding key. A factory whose keys are not
     *     {@code Method}s overrides it to translate one into the other.
     * </p>
     * <p>
     *     Callers may cache the result, so the same method must always resolve to the same key.
     * </p>
     * <p>
     *     The default implementation returns the given method unchanged, which is right for factories whose keys are
     *     {@link java.lang.reflect.Method}s, like the reflection based one.
     * </p>
     *
     * @param method What identifies the method, usually a {@link java.lang.reflect.Method}. Never {@code null}.
     * @return The key this factory uses for the method, or {@code null} if the method has none.
     * @since 1.25.0
     */
    @SuppressWarnings("unchecked")
    default MethodKey resolveMethodKey(Object method) {
        return (MethodKey) method;
    }
}
