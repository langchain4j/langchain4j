package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Optional;

@Internal
public class RagMessageUtils {

    private RagMessageUtils() {}

    public static String singleText(UserMessage userMessage) {
        return findSingleTextInMultimodalMessage(userMessage)
                .map(TextContent::text)
                .orElseGet(userMessage::singleText);
    }

    public static Optional<TextContent> findSingleTextInMultimodalMessage(UserMessage userMessage) {
        if (userMessage.contents().size() == 1) {
            return Optional.empty();
        }

        List<TextContent> textParts = userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .limit(2)
                .toList();
        return textParts.size() == 1 ? Optional.of(textParts.get(0)) : Optional.empty();
    }
}
