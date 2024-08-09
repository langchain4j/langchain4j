package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.sparkdesk.client.chat.ChatCompletionModel;
import dev.langchain4j.model.sparkdesk.client.chat.http.HttpChatCompletionRequest;
import dev.langchain4j.model.sparkdesk.client.chat.http.HttpChatCompletionResponse;
import dev.langchain4j.model.sparkdesk.client.chat.wss.*;
import dev.langchain4j.model.sparkdesk.client.chat.wss.function.Functions;
import dev.langchain4j.model.sparkdesk.shared.RequestHeader;
import dev.langchain4j.model.sparkdesk.shared.RequestMessage;
import dev.langchain4j.model.sparkdesk.spi.SparkdeskAiChatModelBuilderFactory;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.sparkdesk.DefaultSparkdeskAiHelper.*;
import static dev.langchain4j.model.sparkdesk.client.chat.ChatCompletionModel.SPARK_MAX;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

/**
 * Represents an SparkdeskAi language model with a chat completion interface, such as Lite、V2.0、Pro、Max and 4.0 Ultra.
 * You can find description of parameters <a href="https://www.xfyun.cn/doc/spark/%E6%8E%A5%E5%8F%A3%E8%AF%B4%E6%98%8E.html">here</a>.
 */
public class SparkdeskAiChatModel implements ChatLanguageModel {

    private final ChatCompletionModel model;
    private final String appId;
    private final Float temperature;
    private final Integer topK;
    private final Integer maxRetries;
    private final Integer maxToken;
    private final SparkdeskAiHttpClient httpClient;
    private final SparkdeskAiWssClient wssClient;

    @Builder
    public SparkdeskAiChatModel(
            String baseUrl,
            String appId,
            String apiKey,
            String apiSecret,
            Float temperature,
            Integer topK,
            ChatCompletionModel model,
            Integer maxRetries,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.temperature = getOrDefault(temperature, 0.7f);
        this.appId = SparkUtils.isNullOrEmpty(appId, "The appId field cannot be null or an empty string");
        this.topK = getOrDefault(topK, 4);
        this.model = getOrDefault(model, SPARK_MAX);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.maxToken = getOrDefault(maxToken, 4096);
        this.httpClient = SparkdeskAiHttpClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://spark-api-open.xf-yun.com"))
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
        this.wssClient = SparkdeskAiWssClient.builder()
                .baseUrl(model.getWssEndpoint())
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static SparkdeskAiChatModelBuilder builder() {
        for (SparkdeskAiChatModelBuilderFactory factories : loadFactories(SparkdeskAiChatModelBuilderFactory.class)) {
            return factories.get();
        }
        return new SparkdeskAiChatModelBuilder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");

        HttpChatCompletionRequest.Builder requestBuilder = HttpChatCompletionRequest.builder()
                .model(this.model)
                .maxTokens(this.maxToken)
                .stream(false)
                .topK(this.topK)
                .temperature(this.temperature)
                .messages(toSparkdeskiMessages(messages));


        HttpChatCompletionResponse response = withRetry(() -> httpClient.chatCompletion(requestBuilder.build()), maxRetries);
        return Response.from(
                aiMessageFrom(response),
                tokenUsageFrom(response.getUsage())
        );

    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ensureNotEmpty(messages, "messages");

        WssChat chat = WssChat.builder()
                .temperature(this.temperature)
                .domain(this.model.toString())
                .maxTokens(this.maxToken)
                .topK(this.topK)
                .build();

        WssChatCompletionRequest request = WssChatCompletionRequest.builder()
                .header(RequestHeader.builder().appId(this.appId).build())
                .parameter(WssParameter.builder().chat(chat).build())
                .payload(WssRequestPayload.builder()
                        .message(RequestMessage.builder().text(toSparkdeskiMessages(messages)).build())
                        .functions(Functions.builder().text(toFunctions(toolSpecifications)).build())
                        .build())
                .build();


        WssChatCompletionResponse response = withRetry(() -> wssClient.chatCompletion(request), maxRetries);
        return Response.from(
                aiMessageFrom(response),
                tokenUsageFrom(response.getPayload().getUsage().getText())
        );
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, toolSpecification != null ? singletonList(toolSpecification) : null);
    }


    public static class SparkdeskAiChatModelBuilder {
        public SparkdeskAiChatModelBuilder() {
        }
    }
}
