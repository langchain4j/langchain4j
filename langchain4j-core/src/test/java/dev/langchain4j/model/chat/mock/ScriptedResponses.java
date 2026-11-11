package dev.langchain4j.model.chat.mock;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.data.message.AiMessage;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;

/**
 * The responses of a mock, given in order: one per request. Once all were given, the last one is repeated (like
 * Mockito's {@code thenReturn(a, b)}), unless it contains tool execution requests: repeating it would make an AI Service
 * call the tools again and again, so the mock fails instead.
 */
final class ScriptedResponses {

    private final String mockName;
    private final Queue<AiMessage> remaining;
    private AiMessage last;

    ScriptedResponses(String mockName, Collection<AiMessage> responses) {
        this.mockName = mockName;
        this.remaining = new ArrayDeque<>(ensureNotEmpty(responses, "responses"));
    }

    synchronized AiMessage next() {
        AiMessage next = remaining.poll();
        if (next != null) {
            last = next;
            return next;
        }
        if (last.hasToolExecutionRequests()) {
            throw new IllegalStateException(mockName
                    + " has no more responses: the last scripted response (with tool calls) would be repeated, which"
                    + " would loop forever. Script the response that follows the tool results.");
        }
        return last;
    }
}
