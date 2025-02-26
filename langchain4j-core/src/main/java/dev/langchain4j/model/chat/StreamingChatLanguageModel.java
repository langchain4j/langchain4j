package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ListenersUtil;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
                ListenersUtil.onResponse(completeResponse, finalChatRequest, attributes, listeners);
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                ListenersUtil.onError(error, finalChatRequest, attributes, listeners);
                handler.onError(error);
            }
        };

        ListenersUtil.onRequest(finalChatRequest, attributes, listeners);
        doChat(finalChatRequest, observingHandler);
    }

    /**
     * TODO
     *
     * @param chatRequest
     * @param handler
     */
    @Experimental
    void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler);

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

//    @Experimental
//    default void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
//
//        ChatRequestParameters parameters = chatRequest.parameters();
//        validate(parameters);
//        validate(parameters.toolChoice());
//        validate(parameters.responseFormat());
//
//        StreamingResponseHandler<AiMessage> legacyHandler = new StreamingResponseHandler<>() {
//
//            @Override
//            public void onNext(String token) {
//                handler.onPartialResponse(token);
//            }
//
//            @Override
//            public void onComplete(Response<AiMessage> response) {
//                ChatResponse chatResponse = ChatResponse.builder()
//                        .aiMessage(response.content())
//                        .metadata(ChatResponseMetadata.builder()
//                                .tokenUsage(response.tokenUsage())
//                                .finishReason(response.finishReason())
//                                .build())
//                        .build();
//                handler.onCompleteResponse(chatResponse);
//            }
//
//            @Override
//            public void onError(Throwable error) {
//                handler.onError(error);
//            }
//        };
//
//        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
//        if (isNullOrEmpty(toolSpecifications)) {
//            generate(chatRequest.messages(), legacyHandler);
//        } else {
//            if (parameters.toolChoice() == REQUIRED) {
//                if (toolSpecifications.size() != 1) {
//                    throw new UnsupportedFeatureException(
//                            String.format("%s.%s is currently supported only when there is a single tool",
//                                    ToolChoice.class.getSimpleName(), REQUIRED.name()));
//                }
//                generate(chatRequest.messages(), toolSpecifications.get(0), legacyHandler);
//            } else {
//                generate(chatRequest.messages(), toolSpecifications, legacyHandler);
//            }
//        }
//    }

    @Experimental
    default ChatRequestParameters defaultRequestParameters() {
        return ChatRequestParameters.builder().build();
    }

    @Experimental
    default Set<Capability> supportedCapabilities() {
        return Set.of();
    }

    // TODO improve javadoc
}
