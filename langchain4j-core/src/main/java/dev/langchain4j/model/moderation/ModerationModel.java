package dev.langchain4j.model.moderation;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.input.Prompt;

import java.util.List;

public interface ModerationModel {

    Moderation moderate(String text);

    Moderation moderate(Prompt prompt);

    Moderation moderate(Object structuredPrompt);

    Moderation moderate(ChatMessage message);

    Moderation moderate(List<ChatMessage> messages);

    Moderation moderate(TextSegment textSegment);
}
