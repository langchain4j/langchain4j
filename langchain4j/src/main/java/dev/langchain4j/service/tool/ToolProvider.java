package dev.langchain4j.service.tool;

/**
 * A low-level provider of a {@link ToolProviderRequest}.
 */
@FunctionalInterface
public interface ToolProvider {
    /**
     * Receives a request based on a user message and sends relevant tools back. Use can use AI for example.
     *
     * @param request Wraps the UserMessage and ChatMemory
     * @return A wrapper with relevant tools
     */
    ToolProviderResult provideTools(ToolProviderRequest request);
}