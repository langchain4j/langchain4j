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
     * Sends a message from a user to the LLM and returns a response.
     *
     * @param userMessage A user message as a String. Will be wrapped into {@link dev.langchain4j.data.message.UserMessage UserMessage} under the hood.
     * @return Response from the LLM.
     */
    default AiMessage sendUserMessage(String userMessage) {
        return sendUserMessage(UserMessage.from(userMessage));
    }

    /**
     * Sends a message from a user to the LLM and returns a response.
     *
     * @param userMessage A user message.
     * @return Response from the LLM.
     */
    default AiMessage sendUserMessage(UserMessage userMessage) {
        return sendMessages(userMessage);
    }

    /**
     * Sends a structured prompt as a user message to the LLM and returns a response.
     *
     * @param structuredPrompt A user message as an object annotated with {@link dev.langchain4j.model.input.structured.StructuredPrompt @StructuredPrompt}. Will be converted into {@link dev.langchain4j.data.message.UserMessage UserMessage} under the hood.
     * @return Response from the LLM.
     */
    default AiMessage sendUserMessage(Object structuredPrompt) {
        Prompt prompt = toPrompt(structuredPrompt);
        return sendUserMessage(prompt.toUserMessage());
    }

    /**
     * Sends a sequence of messages to the LLM and returns a response.
     * Typically, the sequence contains messages in the following order:
     * System (optional) -> User -> AI -> User -> AI -> User ...
     *
     * @param messages An array of messages to be sent.
     * @return Response from the LLM.
     */
    default AiMessage sendMessages(ChatMessage... messages) {
        return sendMessages(asList(messages));
    }

    /**
     * Sends a sequence of messages to the LLM and returns a response.
     * Typically, the sequence contains messages in the following order:
     * System (optional) -> User -> AI -> User -> AI -> User ...
     *
     * @param messages A list of messages to be sent.
     * @return Response from the LLM.
     */
    AiMessage sendMessages(List<ChatMessage> messages);

    /**
     * Sends a sequence of messages to the LLM and returns a response.
     * Typically, the sequence contains messages in the following order:
     * System (optional) -> User -> AI -> User -> AI -> User ...
     *
     * @param messages           A list of messages to be sent.
     * @param toolSpecifications A list of tools that the LLM is allowed to execute.
     * @return Response from the LLM. AiMessage can contain either a textual response or a request to execute a tool.
     */
    AiMessage sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications);
}
