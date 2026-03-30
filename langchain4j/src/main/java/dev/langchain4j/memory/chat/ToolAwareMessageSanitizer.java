package dev.langchain4j.memory.chat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Self-healing utility for chat message history stored in a {@link dev.langchain4j.store.memory.chat.ChatMemoryStore}.
 *
 * <h2>Problem (Issue #3133)</h2>
 * <p>
 * When bulk tool execution pushes a large number of {@link ToolExecutionResultMessage}s into a bounded
 * memory window, the sliding-window eviction in {@link MessageWindowChatMemory} may evict the parent
 * {@link AiMessage} that contains the originating {@link ToolExecutionRequest}(s) before all
 * of its result messages have been added. The result is a persisted history in which orphaned
 * ToolExecutionResultMessages appear at the head of the list with no corresponding
 * AiMessage. Any subsequent call to the LLM then fails hard:
 * </p>
 * <pre>
 * InvalidRequestException: Invalid parameter: messages with role 'tool' must be
 * a response to a preceding message with 'tool_calls'.
 * </pre>
 * <p>
 * The existing ensureCapacity eviction handles forward cascades correctly (when
 * an AiMessage is evicted its following results are also evicted). However, it
 * cannot repair state that was already persisted in corrupt form by an earlier session or
 * an older library version.
 * </p>
 *
 * <h2>Fix</h2>
 * <p>
 * {@link #sanitize(List)} performs a two-pass O(n) scan:
 * 1. Collect all tool-call IDs that are referenced by AiMessages still present in the list.
 * 2. Drop any ToolExecutionResultMessage whose ID has no match in that set.
 * The method is idempotent, allocation-free when no repair is needed (returns the original list),
 * and logs a WARN for each dropped message so operators can track memory corruption events.
 * </p>
 *
 * @see MessageWindowChatMemory
 * @see <a href="https://github.com/langchain4j/langchain4j/issues/3133">langchain4j#3133</a>
 */
public final class ToolAwareMessageSanitizer {

    private static final Logger log = LoggerFactory.getLogger(ToolAwareMessageSanitizer.class);

    private ToolAwareMessageSanitizer() {}

    /**
     * Returns a view of messages with all orphaned ToolExecutionResultMessages removed.
     *
     * A ToolExecutionResultMessage is considered orphaned when no
     * AiMessage currently in the list contains a ToolExecutionRequest
     * whose id() matches the result's id().
     *
     * This method is O(n) in the number of messages and performs
     * zero allocations when the list is already valid.
     *
     * @param messages the raw message list loaded from a ChatMemoryStore
     * @return the original list (if valid) or a new sanitized list (if orphans were removed)
     */
    public static List<ChatMessage> sanitize(List<ChatMessage> messages) {
        // Pass 1 - O(n): collect every tool-call ID that has a live AiMessage parent.
        Set<String> coveredToolCallIds = new HashSet<>();
        for (ChatMessage message : messages) {
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    String id = request.id();
                    if (id != null) {
                        coveredToolCallIds.add(id);
                    }
                }
            }
        }

        // Pass 2 - O(n): build sanitized list, skipping orphaned tool results.
        // Avoid allocating a new list when nothing needs to be removed (happy path).
        List<ChatMessage> sanitized = null;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message instanceof ToolExecutionResultMessage result) {
                String id = result.id();
                boolean isOrphaned = id == null || !coveredToolCallIds.contains(id);
                if (isOrphaned) {
                    log.warn(
                        "[langchain4j] Removing orphaned ToolExecutionResultMessage (id={}) from chat memory. "
                        + "Its parent AiMessage with tool_calls was already evicted. "
                        + "This self-healing prevents a permanent corrupt-memory state. "
                        + "See https://github.com/langchain4j/langchain4j/issues/3133",
                        id
                    );
                    if (sanitized == null) {
                        // First orphan found - materialise the prefix we've passed already.
                        sanitized = new ArrayList<>(messages.size());
                        sanitized.addAll(messages.subList(0, i));
                    }
                    // skip this message
                    continue;
                }
            }
            if (sanitized != null) {
                sanitized.add(message);
            }
        }

        return sanitized != null ? sanitized : messages;
    }
}
