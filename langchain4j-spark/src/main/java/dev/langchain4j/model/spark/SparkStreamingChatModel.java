package dev.langchain4j.model.spark;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.github.briqt.spark4j.SparkClient;
import io.github.briqt.spark4j.constant.SparkApiVersion;
import io.github.briqt.spark4j.listener.SparkBaseListener;
import io.github.briqt.spark4j.model.SparkMessage;
import io.github.briqt.spark4j.model.SparkRequestBuilder;
import io.github.briqt.spark4j.model.request.SparkRequest;
import io.github.briqt.spark4j.model.response.SparkResponse;
import io.github.briqt.spark4j.model.response.SparkResponseFunctionCall;
import io.github.briqt.spark4j.model.response.SparkResponseUsage;
import lombok.Builder;
import okhttp3.WebSocket;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author ren
 * @since 2024/3/15 16:25
 */
public class SparkStreamingChatModel implements StreamingChatLanguageModel {
    private final SparkClient client;

    private final String appId;
    private final String apiKey;
    private final String apiSecret;
    private final Double temperature;
    private final Integer topK;
    private final SparkApiVersion apiVersion;

    @Builder
    public SparkStreamingChatModel(String appId, String apiKey, String apiSecret, Double temperature, Integer topK, SparkApiVersion apiVersion) {
        if (Utils.isNullOrBlank(appId) || Utils.isNullOrBlank(apiKey) || Utils.isNullOrBlank(apiSecret)) {
            throw new IllegalArgumentException("appId,apikey,apiSecret must be defined.");
        }
        this.appId = appId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.temperature = Utils.getOrDefault(temperature, 0.7);
        this.topK = Utils.getOrDefault(topK, 3);
        this.apiVersion = Utils.getOrDefault(apiVersion, SparkApiVersion.V3_5);

        client = newClient();
    }


    private SparkClient newClient() {
        SparkClient sparkClient = new SparkClient();
        sparkClient.apiKey = this.apiKey;
        sparkClient.apiSecret = this.apiSecret;
        sparkClient.appid = this.appId;
        return sparkClient;
    }


    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        this.generate(messages, new ArrayList<>(), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        SparkRequestBuilder builder = new SparkRequestBuilder().topK(topK).temperature(temperature).apiVersion(apiVersion)
                .messages(SparkUtils.toSparkMessage(messages));
        if (!Utils.isNullOrEmpty(toolSpecifications)) {
            toolSpecifications.forEach(tool -> builder.addFunction(SparkUtils.toSparkFunction(tool)));
        }
        client.chatStream(builder.build(), new SparkStreamingListenAdapter(handler));
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, Collections.singletonList(toolSpecification), handler);
    }

    public static class SparkStreamingChatModelBuilder {
    }

    /**
     * @author ren
     * @since 2024/3/15 16:42
     */
    public static class SparkStreamingListenAdapter extends SparkBaseListener {
        private final StreamingResponseHandler<AiMessage> handler;
        private final StringBuffer contentBuffer;
        private List<ToolExecutionRequest> toolExecutionRequests;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        public SparkStreamingListenAdapter(StreamingResponseHandler<AiMessage> handler) {
            super();
            this.handler = handler;
            this.contentBuffer = new StringBuffer();
            this.tokenUsage = new TokenUsage();
        }


        @Override
        public void onMessage(String content, SparkResponseUsage usage, Integer status, SparkRequest sparkRequest, SparkResponse sparkResponse, WebSocket webSocket) {
            fromSparkUsage(usage);
            handler.onNext(content);
            contentBuffer.append(content);
            if (Objects.equals(2, status)) {
                finishReason = FinishReason.STOP;
                onComplete();
            }
        }


        @Override
        public void onFunctionCall(SparkResponseFunctionCall functionCall, SparkResponseUsage usage, Integer
                status, SparkRequest sparkRequest, SparkResponse sparkResponse, WebSocket webSocket) {
            if (toolExecutionRequests == null) {
                toolExecutionRequests = new ArrayList<>();
            }
            fromSparkUsage(usage);
            List<SparkMessage> messages = sparkResponse.getPayload().getChoices().getText();
            if (!Utils.isNullOrEmpty(messages)) {
                for (SparkMessage message : messages) {
                    handler.onNext(message.getContent());
                    contentBuffer.append(message.getContent());
                }
            }
            toolExecutionRequests.add(ToolExecutionRequest.builder()
                    .name(functionCall.getName())
                    .arguments(functionCall.getArguments())
                    .build());
            if (Objects.equals(2, status)) {
                finishReason = FinishReason.STOP;
                onComplete();
            }
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, okhttp3.Response response) {
            finishReason = FinishReason.OTHER;
            handler.onError(t);
        }


        private void onComplete() {
            AiMessage message;
            if (Utils.isNullOrEmpty(toolExecutionRequests)) {
                message = new AiMessage(contentBuffer.toString());
            } else {
                message = new AiMessage(toolExecutionRequests);
            }
            handler.onComplete(Response.from(message, tokenUsage, finishReason == null ? FinishReason.STOP : finishReason));
        }

        private void fromSparkUsage(SparkResponseUsage usage) {
            tokenUsage = tokenUsage.add(SparkUtils.toUsage(usage));
        }
    }
}
