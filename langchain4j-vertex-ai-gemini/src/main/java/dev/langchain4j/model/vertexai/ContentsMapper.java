package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.message.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.stream.Collectors.toList;

@Slf4j
class ContentsMapper {

    private static volatile boolean warned = false;

    static List<com.google.cloud.vertexai.api.Content> map(List<ChatMessage> messages) {

        List<SystemMessage> systemMessages = messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .collect(toList());

        if (!systemMessages.isEmpty()) {
            if (!warned) {
                log.warn("Gemini does not support SystemMessage(s). " +
                        "All SystemMessage(s) will be merged into the first UserMessage.");
                warned = true;
            }
            messages = mergeSystemMessagesIntoUserMessage(messages, systemMessages);
        }

        // TODO what if only a single system message?

        return messages.stream()
                .map(message -> com.google.cloud.vertexai.api.Content.newBuilder()
                        .setRole(RoleMapper.map(message.type()))
                        .addAllParts(PartsMapper.map(message))
                        .build())
                .collect(toList());
    }

    private static List<ChatMessage> mergeSystemMessagesIntoUserMessage(List<ChatMessage> messages,
                                                                        List<SystemMessage> systemMessages) {
        AtomicBoolean injected = new AtomicBoolean(false);
        return messages.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .map(message -> {
                    if (injected.get()) {
                        return message;
                    }

                    if (message instanceof UserMessage) {
                        UserMessage userMessage = (UserMessage) message;

                        List<Content> allContents = new ArrayList<>();
                        allContents.addAll(systemMessages.stream()
                                .map(systemMessage -> TextContent.from(systemMessage.text()))
                                .collect(toList()));
                        allContents.addAll(userMessage.contents());

                        injected.set(true);

                        if (userMessage.name() != null) {
                            return UserMessage.from(userMessage.name(), allContents);
                        } else {
                            return UserMessage.from(allContents);
                        }
                    }

                    return message;
                })
                .collect(toList());
    }
}
