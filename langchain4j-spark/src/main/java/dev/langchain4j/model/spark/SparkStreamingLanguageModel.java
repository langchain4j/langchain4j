package dev.langchain4j.model.spark;

import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.github.briqt.spark4j.SparkClient;
import io.github.briqt.spark4j.constant.SparkApiVersion;
import io.github.briqt.spark4j.listener.SparkBaseListener;
import io.github.briqt.spark4j.model.SparkRequestBuilder;
import io.github.briqt.spark4j.model.request.SparkRequest;
import io.github.briqt.spark4j.model.response.SparkResponse;
import io.github.briqt.spark4j.model.response.SparkResponseUsage;
import lombok.Builder;
import okhttp3.WebSocket;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Objects;

/**
 * @author ren
 * @since 2024/3/15 16:25
 */
public class SparkStreamingLanguageModel implements StreamingLanguageModel {
    private final SparkClient client;

    private final String appId;
    private final String apiKey;
    private final String apiSecret;
    private final Double temperature;
    private final Integer topK;
    private final SparkApiVersion apiVersion;

    @Builder
    public SparkStreamingLanguageModel(String appId, String apiKey, String apiSecret, Double temperature, Integer topK, SparkApiVersion apiVersion) {
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


    /**
     * Generates a response from the model based on a prompt.
     *
     * @param prompt  The prompt.
     * @param handler The handler for streaming the response.
     */
    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        generate(Prompt.from(prompt), handler);
    }

    /**
     * Generates a response from the model based on a prompt.
     *
     * @param prompt  The prompt.
     * @param handler The handler for streaming the response.
     */
    @Override
    public void generate(Prompt prompt, StreamingResponseHandler<String> handler) {
        SparkRequestBuilder builder = new SparkRequestBuilder().topK(topK).temperature(temperature).apiVersion(apiVersion)
                .messages(Collections.singletonList(SparkUtils.toSparkMessage(prompt.toUserMessage())));
        client.chatStream(builder.build(), new SparkStreamingListenAdapter(handler));
    }

    public static class SparkStreamingLanguageModelBuilder {
        public SparkStreamingLanguageModelBuilder() {
        }
    }


    /**
     * @author ren
     * @since 2024/3/15 16:42
     */
    public static class SparkStreamingListenAdapter extends SparkBaseListener {
        private final StreamingResponseHandler<String> handler;
        private final StringBuffer contentBuffer;
        private TokenUsage tokenUsage;
        private FinishReason finishReason;

        public SparkStreamingListenAdapter(StreamingResponseHandler<String> handler) {
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
        public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, okhttp3.Response response) {
            finishReason = FinishReason.OTHER;
            handler.onError(t);
        }

        private void onComplete() {
            handler.onComplete(dev.langchain4j.model.output.Response.from(contentBuffer.toString(), tokenUsage, finishReason == null ? FinishReason.STOP : finishReason));
        }

        private void fromSparkUsage(SparkResponseUsage usage) {
            tokenUsage = tokenUsage.add(SparkUtils.toUsage(usage));
        }
    }
}
