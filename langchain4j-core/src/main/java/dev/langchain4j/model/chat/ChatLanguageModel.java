package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Result;

import java.util.List;

import static java.util.Arrays.asList;

/**
 * Represents a language model that has a chat interface.
 */
public interface ChatLanguageModel {

    /**
     * Generates a response from the model to a message from a user.
     *
     * @param userMessage A message from the user.
     * @return A generated response from the model.
     */
    default String generate(String userMessage) {
        return generate(UserMessage.from(userMessage)).get().text();
    }

    /**
     * Generates a response from the model based on a sequence of messages.
     * Typically, the sequence contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages An array of messages.
     * @return A generated response from the model.
     */
    default Result<AiMessage> generate(ChatMessage... messages) {
        return generate(asList(messages));
    }

    /**
     * Generates a response from the model based on a sequence of messages.
     * Typically, the sequence contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages A list of messages.
     * @return A generated response from the model.
     */
    Result<AiMessage> generate(List<ChatMessage> messages);

    /**
     * Generates a response from the model based on a sequence of messages.
     * The response may either be a text message or a request to execute one of the specified tools.
     * Typically, the list contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages           A list of messages.
     * @param toolSpecifications A list of tools that the model is allowed to execute.
     *                           The model autonomously decides whether to use any of these tools.
     * @return A generated response from the model. AiMessage can contain either a textual response or a request to execute one of the tools.
     */
    Result<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications);

    /**
     * Generates a response from the model based on a sequence of messages. The model is forced to execute a specified tool.
     * Typically, the list contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages          A list of messages.
     * @param toolSpecification The specification of a tool that must be executed. The model is forced to execute this tool.
     * @return The response from the model, which contains a request to execute a tool.
     */
    Result<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification);
}
