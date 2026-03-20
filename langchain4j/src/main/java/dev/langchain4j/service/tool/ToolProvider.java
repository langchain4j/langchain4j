package dev.langchain4j.service.tool;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.MemoryId;

/**
 * A tool provider. It is called when an AI service is invoked and supplies tools for that invocation.
 * <p>
 * Tools returned in {@link ToolProviderResult} will be included in the request to the LLM.
 * <p>
 * By default, a tool provider is called once at the beginning of an AI service invocation.
 * Override {@link #isDynamic()} to return {@code true} if this provider should be re-evaluated
 * before each LLM call in the tool execution loop.
 * A dynamic provider can return different tools based on the current conversation state.
 */
@FunctionalInterface
public interface ToolProvider {

    /**
     * Provides tools for the request to the LLM.
     *
     * @param request the {@link ToolProviderRequest}, contains {@link UserMessage},
     *                {@link ChatMemory} ID (see {@link MemoryId}) and {@link InvocationParameters}.
     * @return {@link ToolProviderResult} contains tools that should be included in the request to the LLM.
     */
    ToolProviderResult provideTools(ToolProviderRequest request);

    /**
     * Whether this tool provider is dynamic (i.e., should be re-evaluated before each LLM call
     * in the tool execution loop).
     * <p>
     * A static provider (default, returns {@code false}) is called once per AI service invocation.
     * A dynamic provider (returns {@code true}) has its {@link #provideTools(ToolProviderRequest)}
     * called before each LLM call, allowing it to return different tools based on
     * the current conversation state.
     *
     * @return {@code true} if this provider should be re-evaluated before each LLM call
     * @since 1.13.0
     */
    default boolean isDynamic() {
        return false;
    }
}
