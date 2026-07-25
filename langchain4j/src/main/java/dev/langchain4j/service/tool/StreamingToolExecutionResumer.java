package dev.langchain4j.service.tool;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.TokenStream;

/**
 * Streaming counterpart of {@link ToolExecutionResumer} for AI Services backed by a
 * {@code StreamingChatModel}. Resumes a conversation suspended by a {@link ReturnBehavior#SUSPEND}
 * tool; requires the AI Service to be configured with a {@code ChatMemory}.
 *
 * @since 1.18.0
 */
@Experimental
public interface StreamingToolExecutionResumer {

    /**
     * Fulfills the pending tool call with its real result and streams another turn of the AI Service
     * execution loop.
     *
     * @param memoryId   the id of the chat memory holding the suspended conversation.
     * @param toolResult the result of the previously suspended tool call.
     * @return the {@link TokenStream} of the resumed conversation.
     */
    TokenStream resume(Object memoryId, ToolExecutionResultMessage toolResult);
}
