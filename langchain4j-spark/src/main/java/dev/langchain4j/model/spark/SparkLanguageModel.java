package dev.langchain4j.model.spark;

import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.github.briqt.spark4j.SparkClient;
import io.github.briqt.spark4j.constant.SparkApiVersion;
import io.github.briqt.spark4j.constant.SparkMessageRole;
import io.github.briqt.spark4j.model.SparkMessage;
import io.github.briqt.spark4j.model.SparkSyncChatResponse;
import io.github.briqt.spark4j.model.request.SparkRequest;
import lombok.Builder;

import java.util.Collections;

/**
 * @author ren
 * @since 2024/3/15 14:20
 */
public class SparkLanguageModel implements LanguageModel {

    private final SparkClient client;

    private final String appId;
    private final String apiKey;
    private final String apiSecret;
    private final Double temperature;
    private final Integer maxRetries;
    private final Integer topK;
    private final SparkApiVersion apiVersion;

    @Builder
    public SparkLanguageModel(String appId, String apiKey, String apiSecret, Double temperature, Integer maxRetries, Integer topK, SparkApiVersion apiVersion) {
        if (Utils.isNullOrBlank(appId) || Utils.isNullOrBlank(apiKey) || Utils.isNullOrBlank(apiSecret)) {
            throw new IllegalArgumentException("appId,apikey,apiSecret must be defined.");
        }
        this.appId = appId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.temperature = Utils.getOrDefault(temperature, 0.7);
        this.maxRetries = Utils.getOrDefault(maxRetries, 3);
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
    public Response<String> generate(String prompt) {
        SparkRequest request = SparkRequest.builder().topK(topK).apiVersion(apiVersion).temperature(temperature).messages(Collections.singletonList(new SparkMessage(SparkMessageRole.USER, prompt))).build();
        SparkSyncChatResponse response = RetryUtils.withRetry(() -> client.chatSync(request), maxRetries);
        if (response.isOk()) {
            return new Response<>(response.getContent(),
                    SparkUtils.toUsage(response.getTextUsage())
                    , FinishReason.STOP);
        } else {
            return new Response<>(response.getContent(), new TokenUsage(), FinishReason.TOOL_EXECUTION);
        }
    }

    public static class SparkLanguageModelBuilder {
        public SparkLanguageModelBuilder() {
        }
    }
}
