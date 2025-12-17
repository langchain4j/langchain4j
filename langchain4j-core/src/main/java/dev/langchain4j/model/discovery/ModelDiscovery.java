package dev.langchain4j.model.discovery;

import dev.langchain4j.model.ModelProvider;
import java.util.List;

/**
 * Represents a service that can discover available models from an LLM provider.
 * This allows customers to list available models and their capabilities without
 * needing to go to individual AI provider websites.
 *
 * <p>Similar to {@link dev.langchain4j.model.chat.ChatModel} and
 * {@link dev.langchain4j.model.chat.StreamingChatModel}, each provider should
 * implement this interface to enable model discovery.
 *
 * <p>Example usage:
 * <pre>{@code
 * OpenAiModelDiscovery discovery = OpenAiModelDiscovery.builder()
 *     .apiKey(apiKey)
 *     .build();
 *
 * List<ModelDescription> models = discovery.discoverModels();
 *
 * // Filter for specific models
 * ModelDiscoveryFilter filter = ModelDiscoveryFilter.builder()
 *     .types(Set.of(ModelType.CHAT))
 *     .requiredCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
 *     .build();
 * List<ModelDescription> chatModels = discovery.discoverModels(filter);
 * }</pre>
 *
 * @see ModelDescription
 * @see ModelDiscoveryFilter
 */
public interface ModelDiscovery {

    /**
     * Retrieves a list of available models from the provider.
     *
     * @return A list of model descriptions
     * @throws RuntimeException if the discovery operation fails
     */
    List<ModelDescription> discoverModels();

    /**
     * Retrieves a filtered list of available models from the provider.
     *
     * <p>Not all providers support server-side filtering. If filtering is not supported
     * by the provider, implementations may either:
     * <ul>
     *   <li>Ignore the filter and return all models</li>
     *   <li>Apply the filter client-side after fetching all models</li>
     * </ul>
     *
     * <p>Use {@link #supportsFiltering()} to check if server-side filtering is available.
     *
     * @param filter Optional filter to narrow down results.
     *               If null, returns all models (same as {@link #discoverModels()}).
     * @return A list of model descriptions matching the filter criteria
     * @throws RuntimeException if the discovery operation fails
     */
    List<ModelDescription> discoverModels(ModelDiscoveryFilter filter);

    /**
     * Returns the provider for this discovery service.
     *
     * @return The model provider
     */
    ModelProvider provider();

    /**
     * Indicates whether this provider supports server-side filtering during model discovery.
     *
     * <p>If {@code true}, the provider can efficiently filter models on the server side.
     * If {@code false}, filtering (if any) is done client-side after fetching all models.
     *
     * @return true if server-side filtering is supported, false otherwise
     */
    default boolean supportsFiltering() {
        return false;
    }
}
