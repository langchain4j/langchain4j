package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
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

/**
 * Bedrock Streaming chat model
 */
@Slf4j
@Getter
@SuperBuilder
public abstract class AbstractBedrockStreamingChatModel extends AbstractSharedBedrockChatModel implements StreamingChatLanguageModel {

    private volatile BedrockRuntimeAsyncClient asyncClient;

    static class StreamingResponse {
        public String completion;
    }

    @Override
    public void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage(userMessage));
        generate(messages, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        InvokeModelWithResponseStreamRequest request = InvokeModelWithResponseStreamRequest.builder()
                .body(SdkBytes.fromUtf8String(convertMessagesToAwsBody(messages)))
                .modelId(getModelId())
                .contentType("application/json")
                .accept("application/json")
                .build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(request, messages, Collections.emptyList());
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
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
                    ChatModelResponse modelListenerResponse = createModelListenerResponse(
                            null,
                            null,
                            response
                    );
                    ChatModelResponseContext responseContext = new ChatModelResponseContext(
                            modelListenerResponse,
                            modelListenerRequest,
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
                    listenerErrorResponse(throwable, modelListenerRequest, attributes);
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



}
