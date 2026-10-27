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
     * Transforms the text of the given user message, keeping its other contents, like images, in place, as well as
     * its name and attributes. The first text content is transformed, and a message without any text, like a message
     * made only of an image, gets the transformation of an empty text as its first content, unless it is empty.
     */
    private UserMessage transformText(UserMessage userMessage, Object memoryId) {
        List<Content> contents = new ArrayList<>(userMessage.contents());
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i) instanceof TextContent textContent) {
                contents.set(i, TextContent.from(transformUserMessage(textContent.text(), memoryId)));
                return userMessage.toBuilder().contents(contents).build();
            }
        }

        String transformedText = transformUserMessage("", memoryId);
        if (isNullOrEmpty(transformedText)) {
            return userMessage;
        }
        contents.add(0, TextContent.from(transformedText));
        return userMessage.toBuilder().contents(contents).build();
    }

    String transformUserMessage(String userMessage, Object memoryId);
}
