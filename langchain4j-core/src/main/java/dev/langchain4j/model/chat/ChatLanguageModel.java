package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ListenersUtil;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.chat.request.ToolChoice.REQUIRED;

/**
 * Represents a language model that has a chat API.
 *
 * @see StreamingChatLanguageModel
 */
public interface ChatLanguageModel {

    /**
     * This is the main API to interact with the chat model.
     * A temporary default implementation of this method is necessary
     * until all {@link ChatLanguageModel} implementations adopt it. It should be removed once that occurs.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @return a {@link ChatResponse}, containing all the outputs from the LLM
     */
    default ChatResponse chat(ChatRequest chatRequest) {

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        ListenersUtil.onRequest(finalChatRequest, attributes, listeners);
        try {
            ChatResponse chatResponse = doChat(finalChatRequest);
            ListenersUtil.onResponse(chatResponse, finalChatRequest, attributes, listeners);
            return chatResponse;
        } catch (Exception error) {
            ListenersUtil.onError(error, finalChatRequest, attributes, listeners);
            throw error;
        }
    }

    default ChatResponse doChat(ChatRequest chatRequest) {
        throw new RuntimeException("Not implemented");
    }

    default String chat(String userMessage) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(userMessage))
                .build();

        ChatResponse chatResponse = chat(chatRequest);

        return chatResponse.aiMessage().text();
    }

    default ChatResponse chat(ChatMessage... messages) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

        return chat(chatRequest);
    }

    default ChatResponse chat(List<ChatMessage> messages) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

        return chat(chatRequest);
    }

    default List<ChatModelListener> listeners() {
        return Collections.emptyList();
    }

    // TODO
//    default ChatResponse doChat(ChatRequest chatRequest) {
//
//        ChatRequestParameters parameters = chatRequest.parameters();
//        validate(parameters);
//        validate(parameters.toolChoice());
//        validate(parameters.responseFormat());
//
//        Response<AiMessage> response;
//        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
//        if (isNullOrEmpty(toolSpecifications)) {
//            response = generate(chatRequest.messages());
//        } else {
//            if (parameters.toolChoice() == REQUIRED) {
//                if (toolSpecifications.size() != 1) {
//                    throw new UnsupportedFeatureException(
//                            String.format("%s.%s is currently supported only when there is a single tool",
//                                    ToolChoice.class.getSimpleName(), REQUIRED.name()));
//                }
//                response = generate(chatRequest.messages(), toolSpecifications.get(0));
//            } else {
//                response = generate(chatRequest.messages(), toolSpecifications);
//            }
//        }
//
//        return ChatResponse.builder()
//                .aiMessage(response.content())
//                .metadata(ChatResponseMetadata.builder()
//                        .tokenUsage(response.tokenUsage())
//                        .finishReason(response.finishReason())
//                        .build())
//                .build();
//    }

    static void validate(ChatRequestParameters parameters) { // TODO
        String errorTemplate = "%s is not supported yet by this model provider";

        if (parameters.modelName() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'modelName' parameter"));
        }
        if (parameters.temperature() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'temperature' parameter"));
        }
        if (parameters.topP() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'topP' parameter"));
        }
        if (parameters.topK() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'topK' parameter"));
        }
        if (parameters.frequencyPenalty() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'frequencyPenalty' parameter"));
        }
        if (parameters.presencePenalty() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'presencePenalty' parameter"));
        }
        if (parameters.maxOutputTokens() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'maxOutputTokens' parameter"));
        }
        if (parameters.stopSequences() != null) {
            throw new UnsupportedFeatureException(String.format(errorTemplate, "'stopSequences' parameter"));
        }
    }

    static void validate(List<ToolSpecification> toolSpecifications) { // TODO
        if (!isNullOrEmpty(toolSpecifications)) {
            throw new RuntimeException("tools are not supported yet by this model provider");
        }
    }

    static void validate(ToolChoice toolChoice) { // TODO
        if (toolChoice == REQUIRED) {
            throw new UnsupportedFeatureException(String.format("%s.%s is not supported yet by this model provider",
                    ToolChoice.class.getSimpleName(), REQUIRED.name()));
        }
    }

    static void validate(ResponseFormat responseFormat) { // TODO
        String errorTemplate = "%s is not supported yet by this model provider";
        if (responseFormat != null && responseFormat.type() == ResponseFormatType.JSON) {
            // TODO check supportedCapabilities() instead?
            throw new UnsupportedFeatureException(String.format(errorTemplate, "JSON response format"));
        }
    }

    default ChatRequestParameters defaultRequestParameters() {
        return ChatRequestParameters.builder().build();
    }

    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    // TODO improve javadoc
}
