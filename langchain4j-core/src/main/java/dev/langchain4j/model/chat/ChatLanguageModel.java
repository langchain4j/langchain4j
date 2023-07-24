package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.Prompt;

import java.util.List;

import static dev.langchain4j.model.input.structured.StructuredPromptProcessor.toPrompt;
import static java.util.Arrays.asList;

/**
 * Represents a LLM that has a chat interface.
 */
public interface ChatLanguageModel {

    /**
     * Sends a message from a user to the LLM and returns response.
     *
     * @param userMessage User message as a String. Will be wrapped into {@link dev.langchain4j.data.message.UserMessage UserMessage} under the hood.
     * @return {@link dev.langchain4j.data.message.AiMessage AiMessage}
     */
    default AiMessage sendUserMessage(String userMessage) {
        return sendUserMessage(UserMessage.from(userMessage));
    }

    default AiMessage sendUserMessage(UserMessage userMessage) {
        return sendMessages(userMessage);
    }

    /**
     * Sends a structured prompt as a user message to the LLM and returns response.
     *
     * @param structuredPrompt object annotated with {@link dev.langchain4j.model.input.structured.StructuredPrompt @StructuredPrompt}
     * @return {@link dev.langchain4j.data.message.AiMessage AiMessage}
     */
    default AiMessage sendUserMessage(Object structuredPrompt) {
        Prompt prompt = toPrompt(structuredPrompt);
        return sendUserMessage(prompt.toUserMessage());
    }

    default AiMessage sendMessages(ChatMessage... messages) {
        return sendMessages(asList(messages));
    }

    AiMessage sendMessages(List<ChatMessage> messages);

    AiMessage sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications);
}
