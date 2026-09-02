package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/** Utilities for recovering interrupted tool executions in chat memory. */
@Internal
class ChatMemoryUtils {

    private ChatMemoryUtils() {}

    /**
     * Removes incomplete tool execution blocks from the entire message history.
     *
     * <p>A block is incomplete when an {@link AiMessage} with tool requests is followed by too few
     * consecutive {@link ToolExecutionResultMessage}s, or when equally sized request and result
     * lists have complete, unique IDs that do not match.
     */
    static void removeInterruptedToolExecutions(List<ChatMessage> messages) {
        ListIterator<ChatMessage> cursor = messages.listIterator();

        while (cursor.hasNext()) {
            ChatMessage current = cursor.next();
            if (!(current instanceof AiMessage aiMessage) || !aiMessage.hasToolExecutionRequests()) {
                continue;
            }

            List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
            Set<String> resultIds = new HashSet<>();
            boolean resultIdsReliable = true;
            int resultCount = 0;

            while (cursor.hasNext()) {
                ChatMessage next = cursor.next();
                if (next instanceof ToolExecutionResultMessage resultMessage) {
                    resultCount++;
                    if (isNullOrBlank(resultMessage.id()) || !resultIds.add(resultMessage.id())) {
                        resultIdsReliable = false;
                    }
                } else {
                    cursor.previous();
                    break;
                }
            }

            if (isInterrupted(requests, resultCount, resultIds, resultIdsReliable)) {
                for (int i = 0; i <= resultCount; i++) {
                    cursor.previous();
                    cursor.remove();
                }
            }
        }
    }

    private static boolean isInterrupted(
            List<ToolExecutionRequest> requests, int resultCount, Set<String> resultIds, boolean resultIdsReliable) {
        if (resultCount < requests.size()) {
            return true;
        }
        if (resultCount > requests.size() || !resultIdsReliable) {
            return false;
        }

        Set<String> requestIds = new HashSet<>();
        for (ToolExecutionRequest request : requests) {
            if (isNullOrBlank(request.id()) || !requestIds.add(request.id())) {
                return false;
            }
        }
        return !requestIds.equals(resultIds);
    }
}
