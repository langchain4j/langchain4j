package dev.langchain4j.model.moderation;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;

import java.util.List;

public interface ModerationModel {

    Response<Moderation> moderate(String text);

    default Response<Moderation> moderate(Prompt prompt) {
        return moderate(prompt.text());
    }

    @SuppressWarnings("deprecation")
    default Response<Moderation> moderate(ChatMessage message) {
        return moderate(message.text());
    }

    Response<Moderation> moderate(List<ChatMessage> messages);

    default Response<Moderation> moderate(TextSegment textSegment) {
        return moderate(textSegment.text());
    }
}
