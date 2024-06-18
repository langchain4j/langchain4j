package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.model.zhipu.chat.ChatCompletionResponse;
import dev.langchain4j.model.zhipu.spi.ZhipuAiChatModelBuilderFactory;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;
import static dev.langchain4j.model.zhipu.chat.ChatCompletionModel.GLM_4;
import static dev.langchain4j.model.zhipu.chat.ToolChoiceMode.AUTO;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;

/**
 * Represents an ZhipuAi language model with a chat completion interface, such as glm-3-turbo and glm-4.
 * You can find description of parameters <a href="https://open.bigmodel.cn/dev/api">here</a>.
 */
public class ZhipuAiChatModel implements ChatLanguageModel {

    private final String baseUrl;
    private final Double temperature;
    private final Double topP;
    private final String model;
    private final Integer maxRetries;
    private final Integer maxToken;
    private final ZhipuAiClient client;

    @Builder
    public ZhipuAiChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            String model,
            Integer maxRetries,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.baseUrl = getOrDefault(baseUrl, "https://open.bigmodel.cn/");
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.model = getOrDefault(model, GLM_4.toString());
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
        for (ZhipuAiChatModelBuilderFactory factories : loadFactories(ZhipuAiChatModelBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAiChatModelBuilder();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, (ToolSpecification) null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        ensureNotEmpty(messages, "messages");

        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .model(this.model)
                .maxTokens(maxToken)
                .stream(false)
                .topP(topP)
                .temperature(temperature)
                .toolChoice(AUTO)
                .messages(toZhipuAiMessages(messages));

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications));
        }

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(requestBuilder.build()), maxRetries);
        return Response.from(
                aiMessageFrom(response),
                tokenUsageFrom(response.getUsage()),
                finishReasonFrom(response.getChoices().get(0).getFinishReason())
        );
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, toolSpecification != null ? singletonList(toolSpecification) : null);
    }

    public static class ZhipuAiChatModelBuilder {
        public ZhipuAiChatModelBuilder() {
        }
    }
}
