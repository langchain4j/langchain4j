package dev.langchain4j.memory.chat;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Utility methods for cleaning up orphaned tool-related messages in chat memory.
 *
 * @since 1.13.0
 */
@Internal
class ChatMemoryUtils {

    private ChatMemoryUtils() {}

    /**
     * Removes orphaned tool-related messages from the given list (mutates in place).
     *
     * <p>A valid tool block is defined as an {@link AiMessage} with
     * {@code hasToolExecutionRequests() == true}, immediately followed by exactly
     * {@code N} consecutive {@link ToolExecutionResultMessage}(s), where
     * {@code N = aiMessage.toolExecutionRequests().size()}.
     * Completeness is determined by contiguous count, not by {@code tool_call_id} matching,
     * consistent with the existing positional approach in {@code ensureCapacity()}.
     *
     * <p>Any tool-related messages that do not belong to a valid tool block are removed:
     * <ul>
     *   <li>An {@link AiMessage} with tool requests followed by fewer than {@code N}
     *       contiguous {@link ToolExecutionResultMessage}(s)
     *       (the {@code AiMessage} and any partial results are removed)</li>
     *   <li>A standalone {@link ToolExecutionResultMessage} not preceded by an
     *       {@link AiMessage} with tool requests</li>
     *   <li>Excess {@link ToolExecutionResultMessage}(s) beyond the expected count {@code N}</li>
     * </ul>
     *
     * <p>This mirrors the existing forward-direction orphan cleanup in
     * {@code ensureCapacity()}, which removes orphan {@code ToolExecutionResultMessage}(s)
     * when the preceding {@code AiMessage} is evicted due to window overflow.
     *
     * @param messages the mutable list of messages to clean up
     */
    static void removeOrphanedToolMessages(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }

        List<ChatMessage> cleaned = new LinkedList<>();
        ListIterator<ChatMessage> cursor = messages.listIterator();

        while (cursor.hasNext()) {
            ChatMessage current = cursor.next();

            // Standalone ToolExecutionResultMessage without a preceding AiMessage: orphan
            if (current instanceof ToolExecutionResultMessage) {
                continue; // skip (remove)
            }

            // AiMessage with tool execution requests: parse as a tool block
            if (current instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                int expectedResults = aiMessage.toolExecutionRequests().size();
                List<ToolExecutionResultMessage> contiguousResults = new LinkedList<>();

                // Collect consecutive ToolExecutionResultMessages following this AiMessage
                while (cursor.hasNext()) {
                    ChatMessage next = cursor.next();
                    if (next instanceof ToolExecutionResultMessage resultMessage) {
                        contiguousResults.add(resultMessage);
                    } else {
                        // Not a ToolExecutionResultMessage, push back for the outer loop
                        cursor.previous();
                        break;
                    }
                }

                if (contiguousResults.size() < expectedResults) {
                    // Incomplete block: skip the AiMessage and all partial results (remove)
                    continue;
                }

                // Complete block (or more): keep AiMessage + first N results
                cleaned.add(aiMessage);
                for (int i = 0; i < expectedResults; i++) {
                    cleaned.add(contiguousResults.get(i));
                }
                // Any excess results beyond expectedResults are silently dropped
                continue;
            }

            // All other messages (SystemMessage, UserMessage, AiMessage without tools): keep
            cleaned.add(current);
        }

        messages.clear();
        messages.addAll(cleaned);
    }
}
