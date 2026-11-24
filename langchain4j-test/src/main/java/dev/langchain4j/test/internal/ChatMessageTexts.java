package dev.langchain4j.test.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Describes chat messages as text, for judges and failure messages.
 */
@Internal
public final class ChatMessageTexts {

    private ChatMessageTexts() {}

    /**
     * Returns who sent the message: {@code system}, {@code user}, {@code assistant}, {@code tool} or {@code custom}.
     */
    public static String role(ChatMessage message) {
        return switch (message.type()) {
            case SYSTEM -> "system";
            case USER -> "user";
            case AI -> "assistant";
            case TOOL_EXECUTION_RESULT -> "tool";
            case CUSTOM -> "custom";
        };
    }

    /**
     * Receives the parts of a message in their order: its text, and the contents that are not text, such as images.
     */
    public interface PartWriter {

        /**
         * Receives a part of the text of the message.
         */
        void text(String text);

        /**
         * Receives a content that is not text, such as an image, at its position in the message.
         */
        void media(Content content);
    }

    /**
     * Numbers the contents that are not text, per type, for example {@code [image 1]}, {@code [image 2]} and
     * {@code [pdf 1]}.
     */
    public static final class MediaMarkers {

        private final Map<ContentType, Integer> counts = new EnumMap<>(ContentType.class);

        /**
         * Returns the marker of the next content of its type, for example {@code [image 1]}.
         */
        public String next(Content content) {
            int number = counts.merge(content.type(), 1, Integer::sum);
            return "[%s %d]".formatted(typeName(content), number);
        }
    }

    /**
     * Returns the text of the message. Contents that are not text, such as images, are described by their type,
     * for example {@code [image]}. The tool calls of an assistant message are described by the tool name, the call id
     * (when there is one) and the arguments, and the result of a tool execution starts with the tool name and the
     * call id (and says when it is an error), so that several calls of the same tool can be told apart.
     */
    public static String text(ChatMessage message) {
        StringBuilder text = new StringBuilder();
        write(message, new PartWriter() {
            @Override
            public void text(String part) {
                text.append(part);
            }

            @Override
            public void media(Content content) {
                text.append("[").append(typeName(content)).append("]");
            }
        });
        return text.toString();
    }

    /**
     * Writes the parts of the message in their order: the same text as {@link #text(ChatMessage)}, with each content
     * that is not text given to {@link PartWriter#media(Content)} at its position instead of being described.
     */
    public static void write(ChatMessage message, PartWriter writer) {
        if (message instanceof SystemMessage systemMessage) {
            writer.text(systemMessage.text());
        } else if (message instanceof UserMessage userMessage) {
            write(userMessage.contents(), writer);
        } else if (message instanceof AiMessage aiMessage) {
            List<String> parts = new ArrayList<>();
            if (aiMessage.text() != null) {
                parts.add(aiMessage.text());
            }
            aiMessage.toolExecutionRequests().forEach(request -> parts.add(
                    "[calls tool %s%s with %s]".formatted(request.name(), id(request.id()), request.arguments())));
            writer.text(String.join("\n", parts));
        } else if (message instanceof ToolExecutionResultMessage toolResult) {
            writer.text("[%s of tool %s%s] "
                    .formatted(
                            Boolean.TRUE.equals(toolResult.isError()) ? "error result" : "result",
                            toolResult.toolName(),
                            id(toolResult.id())));
            write(toolResult.contents(), writer);
        } else if (message instanceof CustomMessage customMessage) {
            writer.text(String.valueOf(customMessage.attributes()));
        } else {
            writer.text(String.valueOf(message));
        }
    }

    /**
     * Returns the content to send to a judge model: an image whose detail level was not chosen when it was created
     * (see {@link ImageContent#detailLevelSpecified()}) is copied with {@link ImageContent.DetailLevel#AUTO}; any
     * other content is returned as it is.
     */
    public static Content forJudge(Content content) {
        if (content instanceof ImageContent image && !image.detailLevelSpecified()) {
            return ImageContent.from(image.image(), ImageContent.DetailLevel.AUTO);
        }
        return content;
    }

    /**
     * Returns the message to send to a judge model, with its contents changed by {@link #forJudge(Content)}. A
     * message without such contents is returned as it is.
     */
    public static ChatMessage forJudge(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            List<Content> contents = forJudge(userMessage.contents());
            return contents == null ? message : userMessage.toBuilder().contents(contents).build();
        }
        if (message instanceof ToolExecutionResultMessage toolResult) {
            List<Content> contents = forJudge(toolResult.contents());
            return contents == null ? message : toolResult.toBuilder().contents(contents).build();
        }
        return message;
    }

    // null when no content changes
    private static List<Content> forJudge(List<Content> contents) {
        List<Content> changed = contents.stream().map(ChatMessageTexts::forJudge).toList();
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i) != changed.get(i)) {
                return changed;
            }
        }
        return null;
    }

    /**
     * Returns the text of the user messages, or {@code null} for another message.
     */
    public static String userText(ChatMessage message) {
        return message instanceof UserMessage userMessage ? contents(userMessage.contents()) : null;
    }

    /**
     * Describes the messages, one per line ({@code \n}, the same on every operating system), each starting with its
     * role. Contents that are not text are shown by numbered markers, for example {@code [image 1]}, the same way a
     * {@code ChatModelJudge} marks them for the judge model.
     */
    public static String describe(List<? extends ChatMessage> messages) {
        return describe(messages, Integer.MAX_VALUE);
    }

    /**
     * Describes the messages like {@link #describe(List)}, cutting the text of each message to the given length.
     */
    public static String describe(List<? extends ChatMessage> messages, int maxTextLength) {
        MediaMarkers markers = new MediaMarkers();
        List<String> lines = new ArrayList<>();
        for (ChatMessage message : messages) {
            StringBuilder text = new StringBuilder();
            write(message, new PartWriter() {
                @Override
                public void text(String part) {
                    text.append(part);
                }

                @Override
                public void media(Content content) {
                    text.append(markers.next(content));
                }
            });
            lines.add("%s: %s".formatted(role(message), truncate(text.toString(), maxTextLength)));
        }
        return String.join("\n", lines);
    }

    /**
     * Returns the text, cut to the given length, followed by how many characters were cut, for example
     * {@code ...(250 more characters)}. A character made of two {@code char}s (such as an emoji) is not split.
     */
    public static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        int end = maxLength > 0 && Character.isHighSurrogate(text.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return text.substring(0, end) + "...(%d more characters)".formatted(text.length() - end);
    }

    private static String id(String id) {
        return id == null || id.isBlank() ? "" : " (id %s)".formatted(id);
    }

    private static String contents(List<Content> contents) {
        StringBuilder text = new StringBuilder();
        write(contents, new PartWriter() {
            @Override
            public void text(String part) {
                text.append(part);
            }

            @Override
            public void media(Content content) {
                text.append("[").append(typeName(content)).append("]");
            }
        });
        return text.toString();
    }

    private static void write(List<Content> contents, PartWriter writer) {
        for (int i = 0; i < contents.size(); i++) {
            if (i > 0) {
                writer.text("\n");
            }
            Content content = contents.get(i);
            if (content instanceof TextContent textContent) {
                writer.text(textContent.text());
            } else {
                writer.media(content);
            }
        }
    }

    private static String typeName(Content content) {
        return content.type().name().toLowerCase(Locale.ROOT);
    }
}
