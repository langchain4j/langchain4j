package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.model.zhipu.chat.ToolChoiceMode;
import dev.langchain4j.model.zhipu.spi.ZhipuAiStreamingChatModelBuilderFactory;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;
import static dev.langchain4j.model.zhipu.chat.ChatCompletionModel.GLM_4;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@Slf4j
public class ZhipuAiStreamingChatModel implements StreamingChatLanguageModel {

    private final Double temperature;
    private final Double topP;
    private final String model;
    private final List<String> stops;
    private final Integer maxToken;
    private final ZhipuAiClient client;
    private final List<ChatModelListener> listeners;

    @Builder
    public ZhipuAiStreamingChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            List<String> stops,
            String model,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses,
            List<ChatModelListener> listeners
    ) {
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.stops = stops;
        this.model = getOrDefault(model, GLM_4.toString());
        this.maxToken = getOrDefault(maxToken, 512);
        this.listeners = listeners == null ? emptyList() : new ArrayList<>(listeners);
        this.client = ZhipuAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://open.bigmodel.cn/"))
                .apiKey(apiKey)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    public static ZhipuAiStreamingChatModelBuilder builder() {
        for (ZhipuAiStreamingChatModelBuilderFactory factories : loadFactories(ZhipuAiStreamingChatModelBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAiStreamingChatModelBuilder();
    }

    @Override
    public void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
        this.generate(singletonList(UserMessage.from(userMessage)), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        this.generate(messages, (ToolSpecification) null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");

        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(this.model)
                .maxTokens(this.maxToken)
                .stream(true)
                .stop(stops)
                .topP(this.topP)
                .temperature(this.temperature)
                .toolChoice(ToolChoiceMode.AUTO)
                .messages(toZhipuAiMessages(messages));

        if (!isNullOrEmpty(toolSpecifications)) {
            builder.tools(toTools(toolSpecifications));
        }
        ChatCompletionRequest request = builder.build();

        ChatModelRequest modelListenerRequest = createModelListenerRequest(request, messages, toolSpecifications);
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        for (ChatModelListener listener : listeners) {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        }

        client.streamingChatCompletion(request, handler, listeners, requestContext);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        this.generate(messages, toolSpecification == null ? null : singletonList(toolSpecification), handler);
    }

    public static class ZhipuAiStreamingChatModelBuilder {
        public ZhipuAiStreamingChatModelBuilder() {

        }

        public ZhipuAiStreamingChatModelBuilder model(ChatCompletionModel model) {
            this.model = model.toString();
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder model(String model) {
            ensureNotBlank(model, "model");
            this.model = model;
            return this;
        }
    }
}
