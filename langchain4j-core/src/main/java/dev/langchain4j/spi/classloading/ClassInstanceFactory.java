package dev.langchain4j.spi.classloading;

/**
 * A factory for providing instances of classes
 * <p>
 *     Intended to be implemented by downstream frameworks (like Quarkus and Spring) where rather than creating
 *     classes on-the-fly, they will most likely be managed by some dependency injection framework.
 * </p>
 */
public interface ClassInstanceFactory {
    /**
     * Provides an instance of the specified class type.
     *
     * @param <T> the type of the class
     * @param clazz the class object representing the type
     * @return an instance of the specified class type
     */
    <T> T getInstanceOfClass(Class<T> clazz);
}
