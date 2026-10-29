package dev.langchain4j.agentic.internal;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@FunctionalInterface
public interface UserMessageTransformer extends BiFunction<ChatRequest, Object, ChatRequest> {

    @Override
    default ChatRequest apply(ChatRequest chatRequest, Object memoryId) {
        List<ChatMessage> messages = chatRequest.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMessage) {
                UserMessage transformedMessage = transformText(userMessage, memoryId);
                List<ChatMessage> modifiedMessages = chatRequest.messages().stream()
                        .map(message -> message == userMessage ? transformedMessage : message)
                        .toList();
                return ChatRequest.builder()
                        .messages(modifiedMessages)
                        .parameters(chatRequest.parameters())
                        .build();
            }
        }
        return chatRequest;
    }

    /**
     * Transforms the text of the given user message, as returned by {@link #text(UserMessage)}, keeping its other
     * contents, like images, as well as its name and attributes. The transformed text replaces all the text contents
     * with a single one, in the position of the first of them. A message without any text, like a message made only
     * of an image, gets the transformation of an empty text as its first content, unless it is empty.
     */
    private UserMessage transformText(UserMessage userMessage, Object memoryId) {
        String transformedText = transformUserMessage(text(userMessage), memoryId);

        List<Content> contents = new ArrayList<>();
        boolean textAdded = false;
        for (Content content : userMessage.contents()) {
            if (!(content instanceof TextContent)) {
                contents.add(content);
            } else if (!textAdded) {
                contents.add(TextContent.from(transformedText));
                textAdded = true;
            }
        }

        if (!textAdded) {
            if (isNullOrEmpty(transformedText)) {
                return userMessage;
            }
            contents.add(0, TextContent.from(transformedText));
        }
        return userMessage.toBuilder().contents(contents).build();
    }

    /**
     * The text contents of the given user message joined by new lines, or an empty string if it has none: the
     * message can also have other contents, like the image sent to a multimodal agent.
     */
    static String text(UserMessage userMessage) {
        return userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .collect(Collectors.joining("\n"));
    }

    String transformUserMessage(String userMessage, Object memoryId);
}
