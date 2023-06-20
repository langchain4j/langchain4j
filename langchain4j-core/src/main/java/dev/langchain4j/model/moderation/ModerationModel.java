package dev.langchain4j.model.moderation;

import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Result;

public interface ModerationModel {

    Result<Moderation> moderate(String text);

    Result<Moderation> moderate(Prompt prompt);

    Result<Moderation> moderate(Object structuredPrompt);

    Result<Moderation> moderate(ChatMessage chatMessage);

    Result<Moderation> moderate(DocumentSegment documentSegment);
}
