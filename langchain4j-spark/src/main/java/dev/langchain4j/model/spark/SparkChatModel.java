/**
 * @author ren
 * @since 2024/3/15 11:10
 */
package dev.langchain4j.model.spark;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.github.briqt.spark4j.SparkClient;
import io.github.briqt.spark4j.constant.SparkApiVersion;
import io.github.briqt.spark4j.model.SparkRequestBuilder;
import io.github.briqt.spark4j.model.SparkSyncChatResponse;
import lombok.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SparkChatModel implements ChatLanguageModel {

    private final SparkClient client;

    private final String appId;
    private final String apiKey;
    private final String apiSecret;
    private final Double temperature;
    private final Integer maxRetries;
    private final Integer topK;
    private final SparkApiVersion apiVersion;

    @Builder
    public SparkChatModel(String appId, String apiKey, String apiSecret, Double temperature, Integer maxRetries, Integer topK, SparkApiVersion apiVersion) {
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
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, new ArrayList<>());
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        SparkRequestBuilder builder = new SparkRequestBuilder()
                .temperature(temperature)
                .topK(topK)
                .apiVersion(apiVersion)
                .messages(messages.stream().map(SparkUtils::toSparkMessage).collect(Collectors.toList()));
        if (!Utils.isNullOrEmpty(toolSpecifications)) {
            for (ToolSpecification toolSpecification : toolSpecifications) {
                builder.addFunction(SparkUtils.toSparkFunction(toolSpecification));
            }
        }
        SparkSyncChatResponse response = RetryUtils.withRetry(() -> client.chatSync(builder.build()), maxRetries);
        return new Response<>(SparkUtils.toAiMessage(response));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, Collections.singletonList(toolSpecification));
    }

    public static class SparkChatModelBuilder {
        public SparkChatModelBuilder() {
        }
    }
}
