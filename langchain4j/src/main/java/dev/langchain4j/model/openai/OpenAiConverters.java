package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.chat.Message;
import dev.ai4j.openai4j.chat.Role;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;

import java.util.List;

import static dev.ai4j.openai4j.chat.Role.*;
import static java.util.stream.Collectors.toList;

class OpenAiConverters {

    static List<Message> toOpenAiMessages(List<ChatMessage> messages) {

        return messages.stream()
                .map(OpenAiConverters::toOpenAiMessage)
                .collect(toList());
    }

    static Message toOpenAiMessage(ChatMessage message) {

        return Message.builder()
                .role(roleOf(message))
                .content(message.text())
                .build();
    }

    static Role roleOf(ChatMessage message) {
        if (message instanceof SystemMessage) {
            return SYSTEM;
        } else if (message instanceof AiMessage) {
            return ASSISTANT;
        } else {
            return USER;
        }
    }
}
