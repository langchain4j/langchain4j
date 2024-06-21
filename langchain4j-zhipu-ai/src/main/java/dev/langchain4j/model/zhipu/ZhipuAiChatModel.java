package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.model.zhipu.chat.ChatCompletionResponse;
import dev.langchain4j.model.zhipu.shared.ZhipuAiException;
import dev.langchain4j.model.zhipu.spi.ZhipuAiChatModelBuilderFactory;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import retrofit2.HttpException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;
import static dev.langchain4j.model.zhipu.chat.ChatCompletionModel.GLM_4;
import static dev.langchain4j.model.zhipu.chat.ToolChoiceMode.AUTO;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Represents an ZhipuAi language model with a chat completion interface, such as glm-3-turbo and glm-4.
 * You can find description of parameters <a href="https://open.bigmodel.cn/dev/api">here</a>.
 */
@Slf4j
public class ZhipuAiChatModel implements ChatLanguageModel {

    private final String baseUrl;
    private final Double temperature;
    private final Double topP;
    private final String model;
    private final Integer maxRetries;
    private final Integer maxToken;
    private final ZhipuAiClient client;
    private final List<ChatModelListener> listeners;

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
            Boolean logResponses,
            List<ChatModelListener> listeners
    ) {
        this.baseUrl = getOrDefault(baseUrl, "https://open.bigmodel.cn/");
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.model = getOrDefault(model, GLM_4.toString());
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.maxToken = getOrDefault(maxToken, 512);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
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

        ChatCompletionRequest completionRequest = requestBuilder.build();
        ChatModelRequest modelListenerRequest = createModelListenerRequest(completionRequest, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(completionRequest), maxRetries);

        FinishReason finishReason = finishReasonFrom(response.getChoices().get(0).getFinishReason());

        AiMessage content = aiMessageFrom(response);
        if (FinishReason.CONTENT_FILTER.equals(finishReason) || FinishReason.OTHER.equals(finishReason)) {
            Response<AiMessage> errorMessageResponse = Response.from(
                    content,
                    null,
                    finishReason
            );

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    new ZhipuAiException(content.text()),
                    modelListenerRequest,
                    null,
                    attributes
            );


            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    log.warn("Exception while calling model listener", e2);
                }
            });
            return errorMessageResponse;
        }

        Response<AiMessage> messageResponse = Response.from(
                content,
                tokenUsageFrom(response.getUsage()),
                finishReason
        );

        ChatModelResponse modelListenerResponse = createModelListenerResponse(
                response.getId(),
                completionRequest.getModel(),
                messageResponse
        );
        ChatModelResponseContext responseContext = new ChatModelResponseContext(
                modelListenerResponse,
                modelListenerRequest,
                attributes
        );
        listeners.forEach(listener -> {
            try {
                listener.onResponse(responseContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });

        return messageResponse;

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
