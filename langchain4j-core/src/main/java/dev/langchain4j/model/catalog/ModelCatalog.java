package dev.langchain4j.model.catalog;

import java.util.List;

import dev.langchain4j.model.ModelProvider;

/**
 * Represents a service that can discover available models from an LLM provider.
 * This allows customers to list available models and their capabilities without
 * needing to go to individual AI provider websites.
 *
 * <p>Similar to {@link dev.langchain4j.model.chat.ChatModel} and
 * {@link dev.langchain4j.model.chat.StreamingChatModel}, each provider should
 * implement this interface to enable model listing.
 *
 * <p>Example usage:
 * <pre>{@code
 * OpenAiModelCatalog catalog = OpenAiModelCatalog.builder()
 *     .apiKey(apiKey)
 *     .build();
 *
 * List<ModelDescription> models = catalog.listModels();
 *
 * }</pre>
 *
 * @see ModelDescription
 */
public interface ModelCatalog {

    /**
     * Retrieves a list of available models from the provider.
     *
     * <p>Implementations should override this method to provide actual model listing functionality.
     * The default implementation throws {@link UnsupportedOperationException}.
     *
     * @return A list of model descriptions
     * @throws RuntimeException if the listing operation fails
     * @throws UnsupportedOperationException if the provider does not support model listing
     */
    default List<ModelDescription> listModels() {
        throw new UnsupportedOperationException(
                "Model listing is not supported by " + provider() + ". "
                        + "Please check the provider's documentation for available models.");
    }

    /**
     * Returns the provider for this catalog service.
     *
     * @return The model provider
     */
    ModelProvider provider();
}
