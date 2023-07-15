package dev.langchain4j.model.moderation;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Result;

import java.util.List;

public interface ModerationModel {

    Result<Moderation> moderate(String text);

    Result<Moderation> moderate(Prompt prompt);

    Result<Moderation> moderate(Object structuredPrompt);

    Result<Moderation> moderate(ChatMessage message);

    Result<Moderation> moderate(List<ChatMessage> messages);

    Result<Moderation> moderate(TextSegment textSegment);
}
