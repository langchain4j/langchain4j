package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.model.zhipu.chat.ChatCompletionResponse;
import dev.langchain4j.model.zhipu.spi.ZhipuAiChatModelBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
public class ZhipuAiChatModel implements ChatLanguageModel {
    private static final Logger log = LoggerFactory.getLogger(ZhipuAiChatModel.class);

    private final Double temperature;
    private final Double topP;
    private final String model;
    private final Integer maxRetries;
    private final Integer maxToken;
    private final List<String> stops;
    private final ZhipuAiClient client;
    private final List<ChatModelListener> listeners;

    public ZhipuAiChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            String model,
            List<String> stops,
            Integer maxRetries,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses,
            List<ChatModelListener> listeners,
            Duration callTimeout,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout
    ) {
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.stops = stops;
        this.model = getOrDefault(model, GLM_4.toString());
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.maxToken = getOrDefault(maxToken, 512);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.client = ZhipuAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://open.bigmodel.cn/"))
                .apiKey(apiKey)
                .callTimeout(callTimeout)
                .connectTimeout(connectTimeout)
                .writeTimeout(writeTimeout)
                .readTimeout(readTimeout)
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
                .maxTokens(this.maxToken)
                .stream(false)
                .topP(this.topP)
                .stop(this.stops)
                .temperature(this.temperature)
                .toolChoice(AUTO)
                .messages(toZhipuAiMessages(messages));

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.tools(toTools(toolSpecifications));
        }

        ChatCompletionRequest request = requestBuilder.build();
        ChatModelRequest modelListenerRequest = createModelListenerRequest(request, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        for (ChatModelListener chatModelListener : listeners) {
            try {
                chatModelListener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        }

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(request), maxRetries);

        FinishReason finishReason = finishReasonFrom(response.getChoices().get(0).getFinishReason());

        Response<AiMessage> messageResponse = Response.from(
                aiMessageFrom(response),
                tokenUsageFrom(response.getUsage()),
                finishReason
        );

        listeners.forEach(listener -> {
            try {
                if (isSuccessFinishReason(finishReason)) {
                    listener.onResponse(new ChatModelResponseContext(
                            createModelListenerResponse(response.getId(), request.getModel(), messageResponse),
                            modelListenerRequest,
                            attributes
                    ));
                } else {
                    listener.onError(new ChatModelErrorContext(
                            new ZhipuAiException(messageResponse.content().text()),
                            modelListenerRequest,
                            null,
                            attributes
                    ));
                }
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
        private String baseUrl;
        private String apiKey;
        private Double temperature;
        private Double topP;
        private String model;
        private List<String> stops;
        private Integer maxRetries;
        private Integer maxToken;
        private Boolean logRequests;
        private Boolean logResponses;
        private List<ChatModelListener> listeners;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;

        public ZhipuAiChatModelBuilder() {
        }

        public ZhipuAiChatModelBuilder model(ChatCompletionModel model) {
            this.model = model.toString();
            return this;
        }

        public ZhipuAiChatModelBuilder model(String model) {
            ValidationUtils.ensureNotBlank(model, "model");
            this.model = model;
            return this;
        }

        public ZhipuAiChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ZhipuAiChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ZhipuAiChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ZhipuAiChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public ZhipuAiChatModelBuilder stops(List<String> stops) {
            this.stops = stops;
            return this;
        }

        public ZhipuAiChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ZhipuAiChatModelBuilder maxToken(Integer maxToken) {
            this.maxToken = maxToken;
            return this;
        }

        public ZhipuAiChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ZhipuAiChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ZhipuAiChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public ZhipuAiChatModelBuilder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        public ZhipuAiChatModelBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ZhipuAiChatModelBuilder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public ZhipuAiChatModelBuilder writeTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public ZhipuAiChatModel build() {
            return new ZhipuAiChatModel(this.baseUrl, this.apiKey, this.temperature, this.topP, this.model, this.stops, this.maxRetries, this.maxToken, this.logRequests, this.logResponses, this.listeners, this.callTimeout, this.connectTimeout, this.readTimeout, this.writeTimeout);
        }
    }
}
