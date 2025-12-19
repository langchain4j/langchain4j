package dev.langchain4j.model.discovery;

import java.util.List;

import dev.langchain4j.model.ModelProvider;

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
     * Returns the provider for this discovery service.
     *
     * @return The model provider
     */
    ModelProvider provider();
}
