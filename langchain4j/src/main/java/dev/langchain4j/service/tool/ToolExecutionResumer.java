package dev.langchain4j.service.tool;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.Result;

/**
 * Lets an AI Service resume a conversation suspended by a tool declaring {@link ReturnBehavior#SUSPEND}.
 * Requires the AI Service to be configured with a {@code ChatMemory}, where the suspended state is kept.
 *
 * @since 1.18.0
 */
@Experimental
public interface ToolExecutionResumer {

    /**
     * Fulfills the pending tool call with its real result and runs another turn of the AI Service
     * execution loop.
     *
     * @param memoryId   the id of the chat memory holding the suspended conversation.
     * @param toolResult the result of the previously suspended tool call.
     * @return the result of resuming the conversation, possibly suspended again by another
     *         {@link ReturnBehavior#SUSPEND} tool.
     */
    Result<String> resume(Object memoryId, ToolExecutionResultMessage toolResult);
}
