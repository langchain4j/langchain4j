package dev.langchain4j.model.chat;

import dev.langchain4j.MightChangeInTheFuture;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.output.Result;

import java.util.List;

public interface ChatLanguageModel {

    Result<AiMessage> sendUserMessage(String text);

    @MightChangeInTheFuture("not sure this method is useful/needed")
    Result<AiMessage> sendUserMessage(Prompt prompt);

    @MightChangeInTheFuture("not sure this method is useful/needed")
    Result<AiMessage> sendUserMessage(Object structuredPrompt);

    Result<AiMessage> sendMessages(ChatMessage... messages);

    Result<AiMessage> sendMessages(List<ChatMessage> messages);

    Result<AiMessage> sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications);
}
