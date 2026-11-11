package dev.langchain4j.internal;

import static java.util.stream.Collectors.joining;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Converts chat messages into the input of a {@link dev.langchain4j.model.decision.DecisionModel}, the same way for
 * all components that use one.
 */
@Internal
public class DecisionModelInputUtils {

    private DecisionModelInputUtils() {}

    /**
     * The text of a user or assistant message. Content of a user message other than text is represented by a marker,
     * such as {@code [attached image]}. Other messages, and assistant messages without text, give an empty string.
     */
    public static String text(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return markers(userMessage.contents());
        }
        if (message instanceof AiMessage aiMessage && aiMessage.text() != null) {
            return aiMessage.text();
        }
        return "";
    }

    /**
     * Whether the user message contains text, not only other content such as images.
     */
    public static boolean hasText(UserMessage userMessage) {
        return userMessage.contents().stream()
                .anyMatch(content -> content instanceof TextContent textContent
                        && !textContent.text().isBlank());
    }


    /**
     * The messages a decision model receives about a conversation: the system messages that pass the filter, and the
     * last {@code maxMessages} other messages that pass the filter, without the tool results at their start whose AI
     * message is not among them, plus the last user message if it is older (unless {@code maxMessages} is 0). Contents other than text are replaced by a marker, such as {@code [attached image]}.
     * The text of messages other than system messages, and the arguments of tool calls, that are longer than
     * {@code maxMessageLength} characters are shortened in the middle (see {@link #truncate(String, int)}). Custom
     * messages, and AI messages with neither text nor tool calls, are left out.
     */
    public static List<ChatMessage> window(
            List<ChatMessage> messages, Predicate<? super ChatMessage> filter, int maxMessages, int maxMessageLength) {
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> others = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (filter.test(message) && isSendable(message)) {
                if (message instanceof SystemMessage) {
                    systemMessages.add(message);
                } else {
                    others.add(message);
                }
            }
        }
        int start = Math.max(0, others.size() - maxMessages);
        // tool results without the AI message that requested them are left out
        while (start < others.size() && others.get(start) instanceof ToolExecutionResultMessage) {
            start++;
        }
        List<ChatMessage> window = new ArrayList<>(systemMessages);
        if (maxMessages > 0) {
            int lastUserMessage = -1;
            for (int i = others.size() - 1; i >= 0 && lastUserMessage < 0; i--) {
                if (others.get(i) instanceof UserMessage) {
                    lastUserMessage = i;
                }
            }
            if (lastUserMessage >= 0 && lastUserMessage < start) {
                window.add(others.get(lastUserMessage));
            }
        }
        window.addAll(others.subList(start, others.size()));
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage message : window) {
            result.add(message instanceof SystemMessage ? message : truncate(withTextOnly(message), maxMessageLength));
        }
        return result;
    }

    private static ChatMessage truncate(ChatMessage message, int maxLength) {
        if (message instanceof UserMessage userMessage) {
            String text = text(userMessage);
            if (text.length() <= maxLength) {
                return message;
            }
            return userMessage.name() == null
                    ? UserMessage.from(truncate(text, maxLength))
                    : UserMessage.from(userMessage.name(), truncate(text, maxLength));
        }
        if (message instanceof AiMessage aiMessage) {
            List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests().stream()
                    .map(request -> request.arguments() == null || request.arguments().length() <= maxLength
                            ? request
                            : request.toBuilder()
                                    .arguments(truncate(request.arguments(), maxLength))
                                    .build())
                    .toList();
            String text = truncate(aiMessage.text(), maxLength);
            if (text == aiMessage.text() && requests.equals(aiMessage.toolExecutionRequests())) {
                return message;
            }
            return aiMessage.toBuilder().text(text).toolExecutionRequests(requests).build();
        }
        if (message instanceof ToolExecutionResultMessage result) {
            String text = markers(result.contents());
            if (text.length() > maxLength) {
                return result.toBuilder().contents(List.of(TextContent.from(truncate(text, maxLength)))).build();
            }
        }
        return message;
    }

    /**
     * Shortens a text longer than {@code maxLength} characters by keeping its beginning (two thirds) and its end (one
     * third), with a note of about 90 characters in between saying how many characters were left out. A text that
     * would not get shorter is returned as it is. The note says that the assistant saw
     * the full text, so that a decision model does not take the missing part as missing information: in tests, a
     * plain marker made decision models reject answers that were based on the full text.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        int head = maxLength * 2 / 3;
        int tail = maxLength - head;
        if (head > 0 && Character.isHighSurrogate(text.charAt(head - 1))) {
            head--;
        }
        if (tail > 0 && Character.isLowSurrogate(text.charAt(text.length() - tail))) {
            tail--;
        }
        int omitted = text.length() - head - tail;
        String note = "\n[" + omitted + " characters were omitted here to save space; the assistant saw the full text.]\n";
        if (head + note.length() + tail >= text.length()) {
            return text;
        }
        return text.substring(0, head) + note + text.substring(text.length() - tail);
    }

    /**
     * Custom messages are not supported by decision models, and AI messages with neither text nor tool calls (for
     * example, only thinking) have nothing to send.
     */
    private static boolean isSendable(ChatMessage message) {
        if (message instanceof AiMessage aiMessage) {
            return (aiMessage.text() != null && !aiMessage.text().isEmpty()) || aiMessage.hasToolExecutionRequests();
        }
        return message instanceof SystemMessage
                || message instanceof UserMessage
                || message instanceof ToolExecutionResultMessage;
    }

    private static ChatMessage withTextOnly(ChatMessage message) {
        if (message instanceof UserMessage userMessage && !hasOnlyText(userMessage)) {
            return userMessage.name() == null
                    ? UserMessage.from(text(userMessage))
                    : UserMessage.from(userMessage.name(), text(userMessage));
        }
        if (message instanceof ToolExecutionResultMessage result
                && !result.contents().stream().allMatch(TextContent.class::isInstance)) {
            return result.toBuilder().contents(List.of(TextContent.from(markers(result.contents())))).build();
        }
        return message;
    }

    private static String markers(List<Content> contents) {
        return contents.stream()
                .map(content -> content instanceof TextContent textContent
                        ? textContent.text()
                        : "[attached " + content.type().name().toLowerCase(Locale.ROOT) + "]")
                .collect(joining("\n"));
    }

    private static boolean hasOnlyText(UserMessage userMessage) {
        return userMessage.contents().stream().allMatch(TextContent.class::isInstance);
    }

    /**
     * A conversation as a transcript: one map per message, with the images of the conversation referred to by their
     * position, for example {@code [image 1]}. Decision models without native support for conversations send it as
     * JSON. The thinking of assistant messages is left out, and assistant messages with neither text nor tool calls
     * are skipped.
     *
     * @param provider the name of the decision model, used in error messages.
     * @throws UnsupportedFeatureException if a message or a content is neither text nor an image.
     * @throws IllegalArgumentException    if nothing is left to send.
     */
    public static Transcript transcript(List<ChatMessage> messages, String provider) {
        List<Map<String, Object>> entries = new ArrayList<>();
        List<ImageContent> images = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            if (message instanceof SystemMessage systemMessage) {
                entry.put("role", "system");
                entry.put("text", systemMessage.text());
            } else if (message instanceof UserMessage userMessage) {
                entry.put("role", "user");
                if (userMessage.name() != null) {
                    entry.put("name", userMessage.name());
                }
                entry.put("text", transcriptText(userMessage.contents(), images, provider));
            } else if (message instanceof AiMessage aiMessage) {
                if ((aiMessage.text() == null || aiMessage.text().isEmpty()) && !aiMessage.hasToolExecutionRequests()) {
                    continue;
                }
                entry.put("role", "assistant");
                if (aiMessage.text() != null && !aiMessage.text().isEmpty()) {
                    entry.put("text", aiMessage.text());
                }
                if (aiMessage.hasToolExecutionRequests()) {
                    List<Map<String, Object>> toolCalls = new ArrayList<>();
                    for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                        Map<String, Object> toolCall = new LinkedHashMap<>();
                        putIfNotNull(toolCall, "id", request.id());
                        toolCall.put("name", request.name());
                        putIfNotNull(toolCall, "arguments", request.arguments());
                        toolCalls.add(toolCall);
                    }
                    entry.put("tool_calls", toolCalls);
                }
            } else if (message instanceof ToolExecutionResultMessage result) {
                entry.put("role", "tool");
                putIfNotNull(entry, "tool_call_id", result.id());
                entry.put("name", result.toolName());
                entry.put("text", transcriptText(result.contents(), images, provider));
                if (Boolean.TRUE.equals(result.isError())) {
                    entry.put("is_error", true);
                }
            } else {
                throw new UnsupportedFeatureException(provider + " does not support "
                        + message.getClass().getSimpleName() + " in a conversation. Supported messages: "
                        + "SystemMessage, UserMessage, AiMessage and ToolExecutionResultMessage");
            }
            entries.add(entry);
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("The conversation has nothing to send: AiMessages without text and tool "
                    + "execution requests are skipped");
        }
        return new Transcript(entries, images);
    }

    /**
     * A conversation as a transcript, see {@link #transcript(List, String)}.
     */
    public static Transcript transcript(List<ChatMessage> messages) {
        return transcript(messages, "The decision model");
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String transcriptText(List<Content> contents, List<ImageContent> images, String provider) {
        List<String> parts = new ArrayList<>();
        for (Content content : contents) {
            if (content instanceof TextContent textContent) {
                parts.add(textContent.text());
            } else if (content instanceof ImageContent image) {
                images.add(image);
                parts.add("[image " + images.size() + "]");
            } else {
                throw new UnsupportedFeatureException(provider
                        + " supports only text and images in a conversation, but it contains " + content.type());
            }
        }
        return String.join("\n", parts);
    }

    /**
     * A conversation as one map per message, and the images it refers to, in order.
     */
    public static final class Transcript {

        private final List<Map<String, Object>> messages;
        private final List<ImageContent> images;

        Transcript(List<Map<String, Object>> messages, List<ImageContent> images) {
            this.messages = List.copyOf(messages);
            this.images = List.copyOf(images);
        }

        public List<Map<String, Object>> messages() {
            return messages;
        }

        public List<ImageContent> images() {
            return images;
        }
    }
}
