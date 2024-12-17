package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.Set;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static java.util.Arrays.asList;

/**
 * Represents a language model that has a chat API.
 *
 * @see StreamingChatLanguageModel
 */
public interface ChatLanguageModel {

    // TODO improve javadoc

    /**
     * This is the main API to interact with the chat model.
     * All the existing generate(...) methods (see below) will be deprecated and removed before 1.0.0 release.
     * <p>
     * A temporary default implementation of this method is necessary
     * until all {@link ChatLanguageModel} implementations adopt it. It should be removed once that occurs.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @return a {@link ChatResponse}, containing all the outputs from the LLM
     */
    @Experimental
    default ChatResponse chat(ChatRequest chatRequest) {

        ChatParameters chatParameters = chatRequest.parameters();
        validate(chatParameters);
        validate(chatParameters.responseFormat());

        Response<AiMessage> response;
        List<ToolSpecification> toolSpecifications = chatParameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            response = generate(chatRequest.messages());
        } else {
            if (chatParameters.toolChoice() == REQUIRED) {
                if (toolSpecifications.size() != 1) {
                    throw new UnsupportedFeatureException(
                            "ToolChoice.REQUIRED is currently supported only when there is a single tool");
                }
                response = generate(chatRequest.messages(), toolSpecifications.get(0));
            } else {
                response = generate(chatRequest.messages(), toolSpecifications);
            }
        }

        return ChatResponse.builder()
                .aiMessage(response.content())
                .metadata(ChatResponseMetadata.builder()
                        .tokenUsage(response.tokenUsage())
                        .finishReason(response.finishReason())
                        .build())
                .build();
    }

    static void validate(ChatParameters chatParameters) {
        String errorTemplate = "%s is not supported yet by this model provider";

        if (chatParameters.modelName() != null) {
            throw new UnsupportedFeatureException(errorTemplate.formatted("'modelName' parameter"));
        }
        if (chatParameters.temperature() != null) {
            throw new UnsupportedFeatureException(errorTemplate.formatted("'temperature' parameter"));
        }
        if (chatParameters.topP() != null) {
            throw new UnsupportedFeatureException(errorTemplate.formatted("'topP' parameter"));
        }
        if (chatParameters.topK() != null) {
            throw new UnsupportedFeatureException(errorTemplate.formatted("'topK' parameter"));
        }
        if (chatParameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException(errorTemplate.formatted("'frequencyPenalty' parameter"));
        }
        if (chatParameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException(errorTemplate.formatted("'presencePenalty' parameter"));
        }
        if (chatParameters.maxOutputTokens() != null) {
            throw new UnsupportedFeatureException(errorTemplate.formatted("'maxOutputTokens' parameter"));
        }
        if (chatParameters.stopSequences() != null) {
            throw new UnsupportedFeatureException(errorTemplate.formatted("'stopSequences' parameter"));
        }
    }

    static void validate(ResponseFormat responseFormat) {
        String errorTemplate = "%s is not supported yet by this model provider";
        if (responseFormat != null && responseFormat.type() == ResponseFormatType.JSON) {
            // TODO check supportedCapabilities() instead?
            throw new UnsupportedFeatureException(errorTemplate.formatted("JSON response format"));
        }
    }

    @Experimental
    default String chat(String userMessage) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(userMessage))
                .build();

        ChatResponse chatResponse = chat(chatRequest);

        return chatResponse.aiMessage().text();
    }

    @Experimental
    default ChatParameters defaultParameters() {
        return null;
    }

    @Experimental
    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    /**
     * Generates a response from the model based on a message from a user.
     * This is a convenience method that receives the message from a user as a String
     * and returns only the generated response.
     *
     * @param userMessage The message from the user.
     * @return The response generated by the model.
     */
    default String generate(String userMessage) {
        return generate(UserMessage.from(userMessage)).content().text();
    }

    /**
     * Generates a response from the model based on a sequence of messages.
     * Typically, the sequence contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages An array of messages.
     * @return The response generated by the model.
     */
    default Response<AiMessage> generate(ChatMessage... messages) {
        return generate(asList(messages));
    }

    /**
     * Generates a response from the model based on a sequence of messages.
     * Typically, the sequence contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages A list of messages.
     * @return The response generated by the model.
     */
    Response<AiMessage> generate(List<ChatMessage> messages);

    /**
     * Generates a response from the model based on a list of messages and a list of tool specifications.
     * The response may either be a text message or a request to execute one of the specified tools.
     * Typically, the list contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages           A list of messages.
     * @param toolSpecifications A list of tools that the model is allowed to execute.
     *                           The model autonomously decides whether to use any of these tools.
     * @return The response generated by the model.
     * {@link AiMessage} can contain either a textual response or a request to execute one of the tools.
     * @throws UnsupportedFeatureException if tools are not supported by the underlying LLM API
     */
    default Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new UnsupportedFeatureException("tools are currently not supported by " + getClass().getSimpleName());
    }

    /**
     * Generates a response from the model based on a list of messages and a single tool specification.
     * <b>The model is forced to execute the specified tool.
     * This is usually achieved by setting `tool_choice=ANY` in the LLM provider API.</b>
     * <br>
     * Typically, the list contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages          A list of messages.
     * @param toolSpecification The specification of a tool that <b>must</b> be executed.
     *                          The model is <b>forced</b> to execute this tool.
     * @return The response generated by the model.
     * {@link AiMessage} contains a request to execute the specified tool.
     * @throws UnsupportedFeatureException if tools are not supported by the underlying LLM API
     */
    default Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new UnsupportedFeatureException("tools and tool choice are currently not supported by " + getClass().getSimpleName());
    }
}
