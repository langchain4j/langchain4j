package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.zhipu.spi.ZhipuAiStreamingChatModelBuilderFactory;
import dev.langchain4j.spi.ServiceHelper;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.toTools;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.toZhipuAiMessages;

public class ZhipuAiStreamingChatModel implements StreamingChatLanguageModel {

    private final String baseUrl;
    private final Double temperature;
    private final Double topP;
    private final ZhipuAiChatModelEnum model;
    private final Integer maxToken;
    private final ZhipuAiClient client;

    @Builder
    public ZhipuAiStreamingChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            ZhipuAiChatModelEnum model,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.baseUrl = getOrDefault(baseUrl, "https://open.bigmodel.cn/");
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.model = getOrDefault(model, ZhipuAiChatModelEnum.GLM_4);
        this.maxToken = getOrDefault(maxToken, 512);
        this.client = ZhipuAiClient.builder()
                .baseUrl(this.baseUrl)
                .apiKey(apiKey)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static ZhipuAiStreamingChatModelBuilder builder() {
        return ServiceHelper.loadFactoryService(
                ZhipuAiStreamingChatModelBuilderFactory.class,
                ZhipuAiStreamingChatModelBuilder::new
        );
    }

    @Override
    public void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
        this.generate(Collections.singletonList(UserMessage.from(userMessage)), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        this.generate(messages, (ToolSpecification) null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");

        ZhipuAiChatCompletionRequest.ZhipuAiChatCompletionRequestBuilder requestBuilder = ZhipuAiChatCompletionRequest.builder()
                .model(this.model)
                .maxTokens(maxToken)
                .stream(true)
                .topP(topP)
                .temperature(temperature)
                .toolChoice("auto")
                .messages(toZhipuAiMessages(messages));

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications));
        }

        client.streamingChatCompletion(requestBuilder.build(), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        this.generate(messages, toolSpecification == null ? null : Collections.singletonList(toolSpecification), handler);
    }

    public static class ZhipuAiStreamingChatModelBuilder {
        public ZhipuAiStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
