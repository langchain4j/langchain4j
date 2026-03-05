package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

/**
 * Defines the behavior of a tool when {@code dev.langchain4j.service.tool.search.ToolSearchStrategy}
 * is configured for an AI Service.
 *
 * @since 1.12.0
 */
@Experimental
public enum SearchBehavior {

    /**
     * This is the default setting. When {@code ToolSearchStrategy} is configured for an AI Service,
     * all tools configured for that AI Service automatically become searchable.
     * They will not be visible to the LLM until they are found.
     */
    SEARCHABLE,

    /**
     * If you want a tool to always be visible to the LLM, use this setting.
     */
    ALWAYS_VISIBLE;
}
