package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.sparkdesk.client.chat.ChatCompletionModel;
import dev.langchain4j.model.sparkdesk.client.chat.http.HttpChatCompletionRequest;
import dev.langchain4j.model.sparkdesk.spi.SparkdeskAiStreamingChatModelBuilderFactory;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.sparkdesk.DefaultSparkdeskAiHelper.toSparkdeskiMessages;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

public class SparkdeskAiStreamingChatModel implements StreamingChatLanguageModel {

    private final ChatCompletionModel model;
    private final Float temperature;
    private final Integer topK;
    private final Integer maxToken;
    private final SparkdeskAiHttpClient client;

    @Builder
    public SparkdeskAiStreamingChatModel(
            String baseUrl,
            String apiKey,
            String apiSecret,
            Float temperature,
            ChatCompletionModel model,
            Integer topK,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.temperature = getOrDefault(temperature, 0.7f);
        this.topK = getOrDefault(topK, 3);
        this.model = getOrDefault(model, ChatCompletionModel.SPARK_MAX);
        this.maxToken = getOrDefault(maxToken, 4096);
        this.client = SparkdeskAiHttpClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://spark-api-open.xf-yun.com"))
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static SparkdeskAiStreamingChatModelBuilder builder() {
        for (SparkdeskAiStreamingChatModelBuilderFactory factories : loadFactories(SparkdeskAiStreamingChatModelBuilderFactory.class)) {
            return factories.get();
        }
        return new SparkdeskAiStreamingChatModelBuilder();
    }

    @Override
    public void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
        this.generate(singletonList(UserMessage.from(userMessage)), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");

        HttpChatCompletionRequest.Builder builder = HttpChatCompletionRequest.builder()
                .model(this.model)
                .maxTokens(maxToken)
                .stream(true)
                .topK(this.topK)
                .temperature(this.temperature)
                .messages(toSparkdeskiMessages(messages));

        client.streamingChatCompletion(builder.build(), handler);
    }


    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        this.generate(messages, toolSpecification == null ? null : singletonList(toolSpecification), handler);
    }

    public static class SparkdeskAiStreamingChatModelBuilder {
        public SparkdeskAiStreamingChatModelBuilder() {

        }
    }
}
