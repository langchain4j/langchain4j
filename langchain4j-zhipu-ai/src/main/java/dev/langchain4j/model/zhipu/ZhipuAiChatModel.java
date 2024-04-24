package dev.langchain4j.model.zhipu;

import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.ChatCompletionRequest;
import com.zhipu.oapi.service.v4.model.ModelApiResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.spi.ZhipuAiChatModelBuilderFactory;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents an ZhipuAi language model with a chat completion interface, such as glm-3-turbo and glm-4.
 * You can find description of parameters <a href="https://open.bigmodel.cn/dev/api">here</a>.
 */
public class ZhipuAiChatModel implements ChatLanguageModel {

    private final Double temperature;
    private final Double topP;
    private final String model;
    private final Integer maxRetries;
    private final Integer maxToken;
    private final ClientV4 client;

    @Builder
    public ZhipuAiChatModel(
            String apiKey,
            Double temperature,
            Double topP,
            String model,
            Integer maxRetries,
            Integer maxToken
    ) {
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.model = getOrDefault(model, ZhipuAiChatModelName.GLM_4.toString());
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.maxToken = getOrDefault(maxToken, 512);

        this.client = new ClientV4.Builder(apiKey).build();
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

        ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
                .model(this.model)
                .stream(false)
                .invokeMethod(Constants.invokeMethod)
                .messages(toZhipuAiMessages(messages))
                .maxTokens(maxToken)
                .temperature(temperature.floatValue())
                .toolChoice("auto");

        if (topP != null) {
            builder.topP(topP.floatValue());
        }

        if (!isNullOrEmpty(toolSpecifications)) {
            builder.tools(toTools(toolSpecifications));
        }

        ModelApiResponse response = withRetry(() -> client.invokeModelApi(builder.build()), maxRetries);
        return Response.from(
                aiMessageFrom(response.getData()),
                tokenUsageFrom(response.getData().getUsage()),
                finishReasonFrom(response.getData().getChoices().get(0).getFinishReason())
        );
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, toolSpecification != null ? Collections.singletonList(toolSpecification) : null);
    }

    public static class ZhipuAiChatModelBuilder {
        public ZhipuAiChatModelBuilder() {
        }
    }
}
