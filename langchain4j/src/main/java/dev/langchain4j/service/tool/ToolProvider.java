package dev.langchain4j.service.tool;

/**
 * A tool provider. It is called each time the AI service is called and supplies tools for that specific call.
 * <p>
 * Tools returned in {@link ToolProviderResult} will be included in the request to the LLM.
 **/
@FunctionalInterface
public interface ToolProvider {
    /**
     * Provides tools for the request to the LLM.
     *
     * @param request Wraps the UserMessage and ChatMemory
     * @return A wrapper with relevant tools
     */
    ToolProviderResult provideTools(ToolProviderRequest request);
}