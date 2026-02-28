package dev.langchain4j.store.prompt;

import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.spi.services.prompt.PromptResourceLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of {@link PromptResourceLoaderRegistry} that uses SPI-discovered loaders.
 * <p>
 * Loaders are discovered via Java's ServiceLoader mechanism (META-INF/services).
 * The registry is immutable after initialization.
 * <p>
 * Thread-safe for concurrent loading operations.
 */
public class DefaultPromptResourceLoaderRegistry implements PromptResourceLoaderRegistry {

    static final DefaultPromptResourceLoaderRegistry INSTANCE = new DefaultPromptResourceLoaderRegistry();

    private final List<PromptResourceLoader> loaders;

    private DefaultPromptResourceLoaderRegistry() {
        // Initialize with SPI-discovered loaders
        List<PromptResourceLoader> discoveredLoaders = new ArrayList<>(loadFactories(PromptResourceLoader.class));
        // Sort by priority (highest first)
        discoveredLoaders.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        this.loaders = Collections.unmodifiableList(discoveredLoaders);
    }

    private DefaultPromptResourceLoaderRegistry(List<PromptResourceLoader> loaders) {
        // Sort by priority (highest first)
        List<PromptResourceLoader> sortedLoaders = new ArrayList<>(loaders);
        sortedLoaders.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        this.loaders = Collections.unmodifiableList(sortedLoaders);
    }

    /**
     * Creates a new registry with SPI-discovered loaders plus additional custom loaders.
     *
     * @param additionalLoaders custom loaders to add on top of SPI-discovered ones
     * @return a new registry instance with combined loaders
     */
    static DefaultPromptResourceLoaderRegistry withAdditionalLoaders(PromptResourceLoader... additionalLoaders) {
        List<PromptResourceLoader> allLoaders = new ArrayList<>(loadFactories(PromptResourceLoader.class));
        if (additionalLoaders != null && additionalLoaders.length > 0) {
            allLoaders.addAll(Arrays.asList(additionalLoaders));
        }
        return new DefaultPromptResourceLoaderRegistry(allLoaders);
    }

    /**
     * Creates a new registry with only the provided loaders (no SPI discovery).
     *
     * @param loaders the loaders to use in this registry
     * @return a new registry instance with only the provided loaders
     */
    static DefaultPromptResourceLoaderRegistry ofLoaders(PromptResourceLoader... loaders) {
        List<PromptResourceLoader> loaderList =
                (loaders != null && loaders.length > 0) ? Arrays.asList(loaders) : Collections.emptyList();
        return new DefaultPromptResourceLoaderRegistry(loaderList);
    }

    @Override
    public String loadResource(String resource, Class<?> contextClass) {
        String protocol = extractProtocol(resource);

        for (PromptResourceLoader loader : loaders) {
            String loaderProtocol = loader.getProtocol();
            if (Objects.equals(protocol, loaderProtocol)) {
                try {
                    return loader.loadResource(resource, contextClass);
                } catch (Exception e) {
                    throw illegalConfiguration(
                            "Failed to load prompt resource '%s' using loader '%s': %s",
                            resource, loader.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
        }

        throw illegalConfiguration("No loader found for protocol '%s' in resource '%s'", protocol, resource);
    }

    /**
     * Extracts the protocol prefix from a resource string.
     * Returns null if no protocol is present.
     * Handles edge cases like Windows paths (C:\) and URLs.
     *
     * @param resource the resource string
     * @return the protocol (without colon), or null if no protocol
     */
    private static String extractProtocol(String resource) {
        if (resource == null || resource.isEmpty()) {
            return null;
        }

        int colonIndex = resource.indexOf(':');
        if (colonIndex <= 0) {
            // No colon or colon at start
            return null;
        }

        // Check if this looks like a Windows path (single letter followed by colon)
        if (colonIndex == 1 && resource.length() > 2 && (resource.charAt(2) == '\\' || resource.charAt(2) == '/')) {
            // Likely Windows path like "C:\" or "C:/"
            return null;
        }

        String potentialProtocol = resource.substring(0, colonIndex);

        // Protocol should only contain letters, digits, plus, period, or hyphen (RFC 3986)
        if (potentialProtocol.matches("[a-zA-Z][a-zA-Z0-9+.\\-]*")) {
            return potentialProtocol;
        }

        return null;
    }
}
