package dev.langchain4j.classloading;

import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

/**
 * Utility class for returning metadata about a class and its methods. Intended to allow downstream frameworks (like Quarkus
 * or Spring) to use their own mechanisms for providing this information.
 */
public final class ClassMetadataProvider {
    private static final ReflectionBasedClassMetadataProviderFactory DEFAULT_CLASS_METADATA_PROVIDER_FACTORY =
            new ReflectionBasedClassMetadataProviderFactory();

    private ClassMetadataProvider() {}

    /**
     * Retrieves an implementation of a {@link ClassMetadataProviderFactory}. This method first looks for
     * implementations of the factory via the {@link ServiceLoader}. It filters out the default factory implementation
     * ({@link ReflectionBasedClassMetadataProviderFactory}) to allow for custom implementations provided by external frameworks.
     * If no custom implementations are available, the method returns the default factory.
     *
     * @param <MethodKey> The type of the method key, representing a unique identifier for methods.
     * @return An instance of {@link ClassMetadataProviderFactory} either provided by an external framework or falling back
     *         to the default implementation.
     */
    public static <MethodKey> ClassMetadataProviderFactory<MethodKey> getClassMetadataProviderFactory() {
        return ServiceLoader.load(ClassMetadataProviderFactory.class).stream()
                .filter(provider ->
                        !DEFAULT_CLASS_METADATA_PROVIDER_FACTORY.getClass().equals(provider.type()))
                .map(Provider::get)
                .findFirst()
                .orElse(DEFAULT_CLASS_METADATA_PROVIDER_FACTORY);
    }
}
