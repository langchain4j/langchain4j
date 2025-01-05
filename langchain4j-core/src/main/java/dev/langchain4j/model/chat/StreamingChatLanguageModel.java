package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.Set;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.ChatLanguageModel.validate;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;
import static java.util.Collections.singletonList;

/**
 * Represents a language model that has a chat API and can stream a response one token at a time.
 *
 * @see ChatLanguageModel
 */
public interface StreamingChatLanguageModel {

    // TODO improve javadoc

    /**
     * This is the main API to interact with the chat model.
     * All the existing generate(...) methods (see below) will be deprecated and removed before 1.0.0 release.
     * <p>
     * A temporary default implementation of this method is necessary
     * until all {@link StreamingChatLanguageModel} implementations adopt it. It should be removed once that occurs.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @param handler     a {@link StreamingChatResponseHandler} that will handle streaming response from the LLM
     */
    @Experimental
    default void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

        ChatRequestParameters parameters = chatRequest.parameters();
        validate(parameters);
        validate(parameters.toolChoice());
        validate(parameters.responseFormat());

        StreamingResponseHandler<AiMessage> legacyHandler = new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                handler.onPartialResponse(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(response.content())
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(response.tokenUsage())
                                .finishReason(response.finishReason())
                                .build())
                        .build();
                handler.onCompleteResponse(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };

        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        if (isNullOrEmpty(toolSpecifications)) {
            generate(chatRequest.messages(), legacyHandler);
        } else {
            if (parameters.toolChoice() == REQUIRED) {
                if (toolSpecifications.size() != 1) {
                    throw new UnsupportedFeatureException(
                            String.format("%s.%s is currently supported only when there is a single tool",
                                    ToolChoice.class.getSimpleName(), REQUIRED.name()));
                }
                generate(chatRequest.messages(), toolSpecifications.get(0), legacyHandler);
            } else {
                generate(chatRequest.messages(), toolSpecifications, legacyHandler);
            }
        }
    }

    @Experimental
    default ChatRequestParameters defaultRequestParameters() {
        return null;
    }

    @Experimental
    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    /**
     * Generates a response from the model based on a message from a user.
     *
     * @param userMessage The message from the user.
     * @param handler     The handler for streaming the response.
     */
    default void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
        generate(singletonList(UserMessage.from(userMessage)), handler);
    }

    /**
     * Generates a response from the model based on a message from a user.
     *
     * @param userMessage The message from the user.
     * @param handler     The handler for streaming the response.
     */
    default void generate(UserMessage userMessage, StreamingResponseHandler<AiMessage> handler) {
        generate(singletonList(userMessage), handler);
    }

    /**
     * Generates a response from the model based on a sequence of messages.
     * Typically, the sequence contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages A list of messages.
     * @param handler  The handler for streaming the response.
     */
    void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler);

    /**
     * Generates a response from the model based on a list of messages and a list of tool specifications.
     * The response may either be a text message or a request to execute one of the specified tools.
     * Typically, the list contains messages in the following order:
     * System (optional) - User - AI - User - AI - User ...
     *
     * @param messages           A list of messages.
     * @param toolSpecifications A list of tools that the model is allowed to execute.
     *                           The model autonomously decides whether to use any of these tools.
     * @param handler            The handler for streaming the response.
     *                           {@link AiMessage} can contain either a textual response or a request to execute one of the tools.
     * @throws UnsupportedFeatureException if tools are not supported by the underlying LLM API
     */
    default void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        throw new UnsupportedFeatureException("tools are currently not supported by " + getClass().getSimpleName());
    }

    /**
     * Generates a response from the model based on a list of messages and a single tool specification.
     * <b>The model is forced to execute the specified tool.
     * This is usually achieved by setting `tool_choice=ANY` in the LLM provider API.</b>
     *
     * @param messages          A list of messages.
     * @param toolSpecification The specification of a tool that <b>must</b> be executed.
     *                          The model is <b>forced</b> to execute this tool.
     * @param handler           The handler for streaming the response.
     * @throws UnsupportedFeatureException if tools are not supported by the underlying LLM API
     */
    default void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        throw new UnsupportedFeatureException("tools and tool choice are currently not supported by " + getClass().getSimpleName());
    }
}
