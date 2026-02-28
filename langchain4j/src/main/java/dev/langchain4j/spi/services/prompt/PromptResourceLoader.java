package dev.langchain4j.spi.services.prompt;

/**
 * Service Provider Interface (SPI) for loading prompt resources from various sources.
 * <p>
 * Implementations can load prompts from different protocols like "langsmith", "https", "s3", etc.
 * The protocol prefix in the resource string determines which loader is used.
 * <p>
 * Example resource strings:
 * <ul>
 *   <li>"mock:simple-recipe" - handled by loader with protocol "mock"</li>
 *   <li>"https://example.com/prompt.txt" - handled by loader with protocol "https"</li>
 *   <li>"my-prompt.txt" - handled by loader with protocol null (classpath fallback)</li>
 * </ul>
 * <p>
 * Multiple implementations can be registered via Java's ServiceLoader mechanism or
 * programmatically via {@code PromptResourceLoaderRegistry.register()}.
 * The loader with the highest priority matching the protocol will be used.
 *
 * @see java.util.ServiceLoader
 */
public interface PromptResourceLoader {

    /**
     * Priority value used by the built-in classpath loader.
     * <p>
     * Developers can use this constant as a reference point when setting priorities for custom loaders:
     * <ul>
     *   <li>Use a value greater than {@code CLASSPATH_PRIORITY} to override classpath resources</li>
     *   <li>Use a value less than {@code CLASSPATH_PRIORITY} to provide a fallback when classpath loading fails</li>
     * </ul>
     */
    int CLASSPATH_PRIORITY = 0;

    /**
     * Returns the protocol prefix that this loader handles.
     * <p>
     * Protocol should be returned without the colon separator.
     * For example, return "mock" for resources like "mock:resource-name".
     * <p>
     * Return null to handle resources without a protocol prefix (e.g., classpath resources).
     *
     * @return the protocol prefix (without colon), or null for resources without protocol
     */
    String getProtocol();

    /**
     * Loads the prompt resource content.
     *
     * @param resource the resource identifier (may include protocol prefix)
     * @param contextClass the class to use as context for loading (useful for classpath-relative loading)
     * @return the prompt content as a string
     * @throws Exception if the resource cannot be loaded
     */
    String loadResource(String resource, Class<?> contextClass) throws Exception;

    /**
     * Returns the priority of this loader.
     * <p>
     * When multiple loaders support the same protocol, the one with the highest priority is used.
     * Higher values indicate higher priority.
     * <p>
     * Default priority is {@link #CLASSPATH_PRIORITY} (0). The built-in classpath loader also uses
     * this priority. Custom loaders can return higher values to override classpath resources or
     * lower values to act as fallbacks.
     *
     * @return the priority value (higher = higher priority)
     */
    default int getPriority() {
        return CLASSPATH_PRIORITY;
    }
}
