package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ListenersUtil;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * This is the main API to interact with the chat model.
     * <p>
     * A temporary default implementation of this method is necessary
     * until all {@link StreamingChatLanguageModel} implementations adopt it. It should be removed once that occurs.
     *
     * @param chatRequest a {@link ChatRequest}, containing all the inputs to the LLM
     * @param handler     a {@link StreamingChatResponseHandler} that will handle streaming response from the LLM
     */
    @Experimental
    default void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

        ChatRequest finalChatRequest = ChatRequest.builder()
                .messages(chatRequest.messages())
                .parameters(defaultRequestParameters().overrideWith(chatRequest.parameters()))
                .build();

        List<ChatModelListener> listeners = listeners();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();

        StreamingChatResponseHandler observingHandler = new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                ListenersUtil.onResponse(completeResponse, finalChatRequest, system(), attributes, listeners);
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                ListenersUtil.onError(error, finalChatRequest, system(), attributes, listeners);
                handler.onError(error);
            }
        };

        ListenersUtil.onRequest(finalChatRequest, system(), attributes, listeners);
        doChat(finalChatRequest, observingHandler);
    }

    @Experimental
    default void chat(String userMessage, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from(userMessage))
                .build();

        chat(chatRequest, handler);
    }

    @Experimental
    default void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

        chat(chatRequest, handler);
    }

    default List<ChatModelListener> listeners() {
        return Collections.emptyList();
    }

    /**
     * The name of the GenAI system (LLM provider), can be used for observability purposes.
     * By default, each {@link StreamingChatLanguageModel} implementation returns a predefined,
     * OpenTelemetry-compliant name that can be directly used as the OpenTelemetry "gen_ai.system" attribute.
     * See more details
     * <a href="https://opentelemetry.io/docs/specs/semconv/attributes-registry/gen-ai/#gen-ai-system">here</a>.
     */
    default String system() {
        return null;
    }

    @Experimental
    default void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

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
        return ChatRequestParameters.builder().build();
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
     * @deprecated please use {@link #chat(String, StreamingChatResponseHandler)} instead
     */
    @Deprecated(forRemoval = true)
    default void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
        generate(singletonList(UserMessage.from(userMessage)), handler);
    }

    /**
     * Generates a response from the model based on a message from a user.
     *
     * @param userMessage The message from the user.
     * @param handler     The handler for streaming the response.
     * @deprecated please use {@link #chat(List, StreamingChatResponseHandler)} instead
     */
    @Deprecated(forRemoval = true)
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
     * @deprecated please use {@link #chat(List, StreamingChatResponseHandler)} instead
     */
    @Deprecated(forRemoval = true)
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
     * @deprecated please use {@link #chat(ChatRequest, StreamingChatResponseHandler)} instead.
     * See {@link ChatRequestParameters#toolSpecifications()}.
     */
    @Deprecated(forRemoval = true)
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
     * @deprecated please use {@link #chat(ChatRequest, StreamingChatResponseHandler)} instead.
     * See {@link ChatRequestParameters#toolSpecifications()} and {@link ChatRequestParameters#toolChoice()}.
     */
    @Deprecated(forRemoval = true)
    default void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        throw new UnsupportedFeatureException("tools and tool choice are currently not supported by " + getClass().getSimpleName());
    }

    // TODO improve javadoc
}
