package dev.langchain4j.store.prompt;

import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.spi.services.prompt.PromptResourceLoader;

/**
 * Registry interface for loading prompt resources from various sources.
 * <p>
 * Implementations should support protocol-based routing (e.g., "mock:resource", "https://example.com/prompt.txt").
 * <p>
 * Users can provide custom implementations when building {@code AiServices} to control how prompts are loaded.
 * <p>
 * Example: Spring-based registry that loads prompts from a database or external service.
 */
public interface PromptResourceLoaderRegistry {

    /**
     * Loads a prompt resource.
     *
     * @param resource the resource identifier (may include protocol prefix like "mock:resource")
     * @param contextClass the class to use as context for loading
     * @return the prompt content
     * @throws IllegalConfigurationException if resource cannot be loaded or no loader is found
     */
    String loadResource(String resource, Class<?> contextClass);

    /**
     * Returns the default registry implementation that uses SPI-discovered loaders.
     *
     * @return the default registry instance
     */
    static PromptResourceLoaderRegistry getDefault() {
        return DefaultPromptResourceLoaderRegistry.INSTANCE;
    }

    /**
     * Creates a new registry with SPI-discovered loaders plus additional custom loaders.
     * <p>
     * The additional loaders are combined with loaders discovered via Java's ServiceLoader mechanism.
     * All loaders are sorted by priority (highest first) before being used.
     * <p>
     * This is useful when you want to keep the default SPI-based loading behavior but add
     * some custom loaders for specific use cases.
     *
     * @param additionalLoaders custom loaders to add on top of SPI-discovered ones
     * @return a new registry instance with combined loaders
     */
    static PromptResourceLoaderRegistry with(PromptResourceLoader... additionalLoaders) {
        return DefaultPromptResourceLoaderRegistry.withAdditionalLoaders(additionalLoaders);
    }

    /**
     * Creates a new registry with only the provided loaders (no SPI discovery).
     * <p>
     * This creates a registry that uses ONLY the loaders you provide, without discovering
     * any loaders via Java's ServiceLoader mechanism. The loaders are sorted by priority
     * (highest first) before being used.
     * <p>
     * This is useful when you want complete control over which loaders are available,
     * or when you want to avoid the overhead of SPI discovery.
     *
     * @param loaders the loaders to use in this registry
     * @return a new registry instance with only the provided loaders
     */
    static PromptResourceLoaderRegistry of(PromptResourceLoader... loaders) {
        return DefaultPromptResourceLoaderRegistry.ofLoaders(loaders);
    }
}
