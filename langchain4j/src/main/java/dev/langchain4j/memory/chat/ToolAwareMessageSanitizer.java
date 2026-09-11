package dev.langchain4j.memory.chat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repairs a chat message history so that every {@link ToolExecutionRequest} made by an {@link AiMessage} is
 * answered by exactly one {@link ToolExecutionResultMessage}, and every {@code ToolExecutionResultMessage}
 * answers a tool call made by the {@code AiMessage} that precedes it. Most LLM providers reject a history that
 * violates either rule.
 * <p>
 * Corruption can occur in either direction: an {@code AiMessage} whose tool calls were never (or only partially)
 * answered - for example because the application restarted mid tool execution, or two concurrent requests raced
 * on the same memory id - and a {@code ToolExecutionResultMessage} left without its parent {@code AiMessage} -
 * for example because an external store truncated the persisted history.
 * <p>
 * Matching is positional. A {@code ToolExecutionResultMessage} only answers calls made by the {@code AiMessage}
 * that most recently opened, i.e. the nearest preceding {@code AiMessage}, provided no other message type
 * appears in between. A result that shows up anywhere else - before any {@code AiMessage}, or after the window
 * for one has already closed - is treated as orphaned and removed, even if some earlier {@code AiMessage}
 * happens to declare a matching id.
 * <p>
 * When an {@code AiMessage}'s tool calls are only partially answered, the unanswered calls are stripped from it
 * and its text, thinking and attributes are preserved. If none of its calls are answered and it carries no text,
 * the {@code AiMessage} is dropped entirely, since an empty assistant turn is not valid history either.
 * <p>
 * A {@code null} {@link ToolExecutionRequest#id()} or {@link ToolExecutionResultMessage#id()} cannot be reliably
 * correlated with its counterpart, so such entries are left untouched wherever they occur: a null-id call is
 * never considered unanswered and therefore never triggers repair of its message, a null-id result is never
 * considered orphaned, and neither is allowed to match the other.
 * <p>
 * {@link #sanitize(List)} is idempotent and returns the original list instance when no repair is needed.
 *
 * @see MessageWindowChatMemory
 * @see TokenWindowChatMemory
 */
final class ToolAwareMessageSanitizer {

    private static final Logger log = LoggerFactory.getLogger(ToolAwareMessageSanitizer.class);

    private ToolAwareMessageSanitizer() {}

    static List<ChatMessage> sanitize(List<ChatMessage> messages) {
        List<ChatMessage> sanitized = null;
        int size = messages.size();

        int i = 0;
        while (i < size) {
            ChatMessage message = messages.get(i);

            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                int windowStart = i;

                Set<String> unansweredCallIds = new HashSet<>();
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    if (request.id() != null) {
                        unansweredCallIds.add(request.id());
                    }
                }

                Set<String> answeredCallIds = new HashSet<>();
                List<ToolExecutionResultMessage> keptResults = new ArrayList<>();
                boolean orphanResultDropped = false;

                int j = i + 1;
                while (j < size && messages.get(j) instanceof ToolExecutionResultMessage result) {
                    String resultId = result.id();
                    if (resultId == null) {
                        keptResults.add(result);
                    } else if (unansweredCallIds.remove(resultId)) {
                        answeredCallIds.add(resultId);
                        keptResults.add(result);
                    } else {
                        log.warn("Dropping orphaned ToolExecutionResultMessage with id '{}'", resultId);
                        orphanResultDropped = true;
                    }
                    j++;
                }

                // Reaching the end of the list does not close the window: an AiMessage with unanswered
                // tool calls in tail position is indistinguishable from one that is still awaiting its
                // result(s) (e.g. concurrent tool execution, or the result is about to be added on the
                // next turn), so it must be left untouched. Only a subsequent message of a different type
                // proves that no more results are coming and that the remaining calls were abandoned.
                boolean windowClosedByAnotherMessage = j < size;
                boolean aiMessageNeedsRepair = windowClosedByAnotherMessage && !unansweredCallIds.isEmpty();
                if (aiMessageNeedsRepair || orphanResultDropped) {
                    if (sanitized == null) {
                        sanitized = new ArrayList<>(size);
                        sanitized.addAll(messages.subList(0, windowStart));
                    }
                    if (aiMessageNeedsRepair) {
                        AiMessage repaired = repair(aiMessage, answeredCallIds);
                        if (repaired != null) {
                            sanitized.add(repaired);
                        }
                    } else {
                        sanitized.add(aiMessage);
                    }
                    sanitized.addAll(keptResults);
                } else if (sanitized != null) {
                    sanitized.add(aiMessage);
                    sanitized.addAll(keptResults);
                }

                i = j;
                continue;
            }

            if (message instanceof ToolExecutionResultMessage result) {
                String resultId = result.id();
                if (resultId != null) {
                    log.warn("Dropping orphaned ToolExecutionResultMessage with id '{}'", resultId);
                    if (sanitized == null) {
                        sanitized = new ArrayList<>(size);
                        sanitized.addAll(messages.subList(0, i));
                    }
                } else if (sanitized != null) {
                    sanitized.add(message);
                }
                i++;
                continue;
            }

            if (sanitized != null) {
                sanitized.add(message);
            }
            i++;
        }

        return sanitized != null ? sanitized : messages;
    }

    /**
     * Returns a copy of {@code aiMessage} retaining only the tool calls whose id is either {@code null}
     * (untrackable, always kept) or present in {@code answeredCallIds}. Returns {@code null} when nothing
     * would be left to keep and the message has no text, since an assistant turn with neither text nor tool
     * calls is not valid history.
     */
    private static AiMessage repair(AiMessage aiMessage, Set<String> answeredCallIds) {
        List<ToolExecutionRequest> kept = new ArrayList<>();
        int droppedCount = 0;
        for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
            String id = request.id();
            if (id == null || answeredCallIds.contains(id)) {
                kept.add(request);
            } else {
                droppedCount++;
            }
        }

        if (kept.isEmpty()) {
            if (hasText(aiMessage)) {
                log.warn("Dropping {} unanswered tool call(s) from AiMessage, keeping its text", droppedCount);
                return aiMessage.toBuilder().toolExecutionRequests(List.of()).build();
            }
            log.warn("Dropping AiMessage with {} unanswered tool call(s) and no text", droppedCount);
            return null;
        }

        log.warn("Dropping {} unanswered tool call(s) from AiMessage", droppedCount);
        return aiMessage.toBuilder().toolExecutionRequests(kept).build();
    }

    private static boolean hasText(AiMessage aiMessage) {
        return aiMessage.text() != null && !aiMessage.text().isEmpty();
    }
}
