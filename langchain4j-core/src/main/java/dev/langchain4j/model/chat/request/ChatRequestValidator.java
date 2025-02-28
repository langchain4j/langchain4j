package dev.langchain4j.model.chat.request;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;

import java.util.List;
import java.util.Locale;

import static dev.langchain4j.data.message.ContentType.TEXT;

public class ChatRequestValidator {

    public static void validateMessages(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage) {
                for (Content content : userMessage.contents()) {
                    if (content.type() != TEXT) {
                        throw new UnsupportedFeatureException(String.format(
                                "Content of type %s is not supported yet by this model provider",
                                content.type().toString().toLowerCase(Locale.ROOT)));
                    }
                }
            }
        }
    }
}
