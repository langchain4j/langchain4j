package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Utility methods for cleaning up orphaned tool-related messages in chat memory.
 *
 * @since 1.15.0
 */
@Internal
class ChatMemoryUtils {

    private ChatMemoryUtils() {}

    /**
     * Removes orphaned tool-related messages from the given list (mutates in place).
     *
     * <p>A valid tool block is defined as an {@link AiMessage} with
     * {@code hasToolExecutionRequests() == true}, immediately followed by enough
     * consecutive {@link ToolExecutionResultMessage}(s) to match the
     * {@link ToolExecutionRequest}(s).
     * When tool call IDs are available, completeness is determined by matching each
     * {@link ToolExecutionResultMessage#id()} to a corresponding
     * {@link ToolExecutionRequest#id()}. When IDs are unavailable, completeness falls
     * back to the existing contiguous-count behavior. Excess or non-matching results
     * are removed.
     *
     * <p>Any tool-related messages that do not belong to a valid tool block are removed:
     * <ul>
     *   <li>An {@link AiMessage} with tool requests followed by fewer than the expected number of
     *       contiguous {@link ToolExecutionResultMessage}(s)
     *       (the {@code AiMessage} and any partial results are removed)</li>
     *   <li>An {@link AiMessage} with tool requests followed by
     *       {@link ToolExecutionResultMessage}(s) whose IDs do not match the requested tool call IDs</li>
     *   <li>A standalone {@link ToolExecutionResultMessage} not preceded by an
     *       {@link AiMessage} with tool requests</li>
     *   <li>Excess {@link ToolExecutionResultMessage}(s) beyond the expected count</li>
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
                List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
                int expectedResults = toolExecutionRequests.size();
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

                List<ToolExecutionResultMessage> resultsToKeep =
                        resultsMatchingRequests(toolExecutionRequests, contiguousResults);
                if (resultsToKeep.isEmpty()) {
                    // Mismatched block: skip the AiMessage and all contiguous results (remove)
                    continue;
                }

                // Complete block (or more): keep AiMessage + matching results
                cleaned.add(aiMessage);
                cleaned.addAll(resultsToKeep);
                // Any excess or non-matching results are silently dropped
                continue;
            }

            // All other messages (SystemMessage, UserMessage, AiMessage without tools): keep
            cleaned.add(current);
        }

        messages.clear();
        messages.addAll(cleaned);
    }

    private static List<ToolExecutionResultMessage> resultsMatchingRequests(
            List<ToolExecutionRequest> requests, List<ToolExecutionResultMessage> results) {
        RequestIdStatus requestIdStatus = requestIdStatus(requests);
        if (requestIdStatus == RequestIdStatus.UNAVAILABLE) {
            return results.subList(0, requests.size());
        }
        if (requestIdStatus == RequestIdStatus.INVALID) {
            return List.of();
        }

        List<ToolExecutionResultMessage> matchingResults = new ArrayList<>();
        for (ToolExecutionRequest request : requests) {
            ToolExecutionResultMessage matchingResult = firstResultWithId(request.id(), results);
            if (matchingResult == null) {
                return List.of();
            }
            matchingResults.add(matchingResult);
        }

        return matchingResults;
    }

    private static RequestIdStatus requestIdStatus(List<ToolExecutionRequest> requests) {
        Set<String> requestIds = new LinkedHashSet<>();
        int requestIdsAvailable = 0;
        for (ToolExecutionRequest request : requests) {
            String id = request.id();
            if (!isNullOrBlank(id)) {
                requestIdsAvailable++;
                if (!requestIds.add(id)) {
                    return RequestIdStatus.INVALID;
                }
            }
        }

        if (requestIdsAvailable == 0) {
            return RequestIdStatus.UNAVAILABLE;
        }
        if (requestIdsAvailable == requests.size()) {
            return RequestIdStatus.AVAILABLE;
        }

        return RequestIdStatus.INVALID;
    }

    private static ToolExecutionResultMessage firstResultWithId(String id, List<ToolExecutionResultMessage> results) {
        for (ToolExecutionResultMessage result : results) {
            if (id.equals(result.id())) {
                return result;
            }
        }

        return null;
    }

    private enum RequestIdStatus {
        AVAILABLE,
        UNAVAILABLE,
        INVALID
    }
}
