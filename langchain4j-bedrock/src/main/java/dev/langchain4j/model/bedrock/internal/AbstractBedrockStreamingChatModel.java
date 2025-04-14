package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;

import static dev.langchain4j.model.ModelProvider.AMAZON_BEDROCK;

/**
 * Bedrock Streaming chat model
 */
@Slf4j
@Getter
@SuperBuilder
public abstract class AbstractBedrockStreamingChatModel extends AbstractSharedBedrockChatModel implements StreamingChatModel {

    private volatile BedrockRuntimeAsyncClient asyncClient;

    static class StreamingResponse {
        public String completion;
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestValidator.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidator.validateParameters(parameters);
        ChatRequestValidator.validate(parameters.toolSpecifications());
        ChatRequestValidator.validate(parameters.toolChoice());
        ChatRequestValidator.validate(parameters.responseFormat());

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

        generate(chatRequest.messages(), legacyHandler);
    }

    private void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        InvokeModelWithResponseStreamRequest request = InvokeModelWithResponseStreamRequest.builder()
                .body(SdkBytes.fromUtf8String(convertMessagesToAwsBody(messages)))
                .modelId(getModelId())
                .contentType("application/json")
                .accept("application/json")
                .build();

        ChatRequest listenerRequest = createListenerRequest(request, messages, Collections.emptyList());
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext =
                new ChatModelRequestContext(listenerRequest, provider(), attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        StringBuffer finalCompletion = new StringBuffer();

        InvokeModelWithResponseStreamResponseHandler.Visitor visitor = InvokeModelWithResponseStreamResponseHandler.Visitor.builder()
                .onChunk(chunk -> {
                    StreamingResponse sr = Json.fromJson(chunk.bytes().asUtf8String(), StreamingResponse.class);
                    finalCompletion.append(sr.completion);
                    handler.onNext(sr.completion);
                })
                .build();

        InvokeModelWithResponseStreamResponseHandler h = InvokeModelWithResponseStreamResponseHandler.builder()
                .onEventStream(stream -> stream.subscribe(event -> event.accept(visitor)))
                .onComplete(() -> {
                    Response<AiMessage> response = Response.from(new AiMessage(finalCompletion.toString()));
                    ChatResponse listenerResponse = createListenerResponse(
                            null,
                            null,
                            response
                    );
                    ChatModelResponseContext responseContext = new ChatModelResponseContext(
                            listenerResponse,
                            listenerRequest,
                            provider(),
                            attributes
                    );

                    listeners.forEach(listener -> {
                        try {
                            listener.onResponse(responseContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    });
                    handler.onComplete(response);
                })
                .onError(throwable -> {
                    listenerErrorResponse(throwable, listenerRequest, provider(), attributes);
                    handler.onError(throwable);
                })
                .build();
        try {
            getAsyncClient().invokeModelWithResponseStream(request, h).join();
        } catch (RuntimeException e) {
            log.error("Error on bedrock stream request", e);
        }

    }

    public BedrockRuntimeAsyncClient getAsyncClient() {
        if (asyncClient == null) {
            synchronized (this) {
                if (asyncClient == null) {
                    asyncClient = initAsyncClient();
                }
            }
        }
        return asyncClient;
    }

    /**
     * Initialize async bedrock client
     *
     * @return async bedrock client
     */
    private BedrockRuntimeAsyncClient initAsyncClient() {
        BedrockRuntimeAsyncClient client = BedrockRuntimeAsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(c-> c.apiCallTimeout(timeout))
                .build();
        return client;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return AMAZON_BEDROCK;
    }
}
