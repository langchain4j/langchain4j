package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.spi.ZhipuAiChatModelBuilderFactory;
import dev.langchain4j.spi.ServiceHelper;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;

public class ZhipuAiChatModel implements ChatLanguageModel {

    private final String baseUrl;
    private final Double temperature;
    private final Double topP;
    private final ZhipuAiChatModelEnum model;
    private final Integer maxRetries;
    private final Integer maxToken;
    private final ZhipuAiClient client;

    @Builder
    public ZhipuAiChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            ZhipuAiChatModelEnum model,
            Integer maxRetries,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.baseUrl = getOrDefault(baseUrl, "https://open.bigmodel.cn/");
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.model = getOrDefault(model, ZhipuAiChatModelEnum.GLM_4);
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.maxToken = getOrDefault(maxToken, 512);
        this.client = ZhipuAiClient.builder()
                .baseUrl(this.baseUrl)
                .apiKey(apiKey)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static ZhipuAiChatModelBuilder builder() {
        return ServiceHelper.loadFactoryService(
                ZhipuAiChatModelBuilderFactory.class,
                ZhipuAiChatModelBuilder::new
        );
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, (ToolSpecification) null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ensureNotEmpty(messages, "messages");

        ZhipuAiChatCompletionRequest.ZhipuAiChatCompletionRequestBuilder requestBuilder = ZhipuAiChatCompletionRequest.builder()
                .model(this.model)
                .maxTokens(maxToken)
                .stream(false)
                .topP(topP)
                .temperature(temperature)
                .toolChoice("auto")
                .messages(toZhipuAiMessages(messages));

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications));
        }

        ZhipuAiChatCompletionResponse response = withRetry(() -> client.chatCompletion(requestBuilder.build()), maxRetries);
        return Response.from(
                aiMessageFrom(response),
                tokenUsageFrom(response.getUsage()),
                finishReasonFrom(response.getChoices().get(0).getFinishReason())
        );
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, toolSpecification != null ? Collections.singletonList(toolSpecification) : null);
    }

    public static class ZhipuAiChatModelBuilder {
        public ZhipuAiChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
