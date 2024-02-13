package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bedrock Streaming chat model
 */
@Getter
@SuperBuilder
public abstract class AbstractBedrockStreamingChatModel extends AbstractSharedBedrockChatModel implements StreamingChatLanguageModel {
    @Getter
    private final BedrockRuntimeAsyncClient asyncClient = initAsyncClient();

    class StreamingResponse {
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
                    handler.onComplete(Response.from(new AiMessage(finalCompletion.toString())));
                })
                .onError(handler::onError)
                .build();
        asyncClient.invokeModelWithResponseStream(request, h).join();

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
                .build();
        return client;
    }



}
