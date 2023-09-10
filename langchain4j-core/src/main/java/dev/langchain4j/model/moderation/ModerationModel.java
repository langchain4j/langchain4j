package dev.langchain4j.model.moderation;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Response;

import java.util.List;

public interface ModerationModel {

    Response<Moderation> moderate(String text);

    Response<Moderation> moderate(Prompt prompt);

    Response<Moderation> moderate(ChatMessage message);

    Response<Moderation> moderate(List<ChatMessage> messages);

    Response<Moderation> moderate(TextSegment textSegment);
}
