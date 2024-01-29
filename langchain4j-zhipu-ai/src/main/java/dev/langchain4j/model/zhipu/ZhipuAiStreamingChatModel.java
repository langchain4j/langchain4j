package dev.langchain4j.model.zhipu;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.zhipu.chat.ChatCompletionModel;
import dev.langchain4j.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.model.zhipu.chat.ToolChoiceMode;
import dev.langchain4j.model.zhipu.spi.ZhipuAiStreamingChatModelBuilderFactory;
import dev.langchain4j.spi.ServiceHelper;

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
    private final String model;
    private final Integer maxToken;
    private final ZhipuAiClient client;

    public ZhipuAiStreamingChatModel(
            String baseUrl,
            String apiKey,
            Double temperature,
            Double topP,
            String model,
            Integer maxToken,
            Boolean logRequests,
            Boolean logResponses
    ) {
        this.baseUrl = getOrDefault(baseUrl, "https://open.bigmodel.cn/");
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.model = getOrDefault(model, ChatCompletionModel.GLM_4.toString());
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

        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(this.model)
                .maxTokens(maxToken)
                .stream(true)
                .topP(topP)
                .temperature(temperature)
                .toolChoice(ToolChoiceMode.AUTO)
                .messages(toZhipuAiMessages(messages));

        if (!isNullOrEmpty(toolSpecifications)) {
            builder.tools(toTools(toolSpecifications));
        }

        client.streamingChatCompletion(builder.build(), handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        this.generate(messages, toolSpecification == null ? null : Collections.singletonList(toolSpecification), handler);
    }

    public static class ZhipuAiStreamingChatModelBuilder {
        private String baseUrl;
        private String apiKey;
        private Double temperature;
        private Double topP;
        private String model;
        private Integer maxToken;
        private Boolean logRequests;
        private Boolean logResponses;

        public ZhipuAiStreamingChatModelBuilder() {
        }

        public ZhipuAiStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder maxToken(Integer maxToken) {
            this.maxToken = maxToken;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ZhipuAiStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public ZhipuAiStreamingChatModel build() {
            return new ZhipuAiStreamingChatModel(this.baseUrl, this.apiKey, this.temperature, this.topP, this.model, this.maxToken, this.logRequests, this.logResponses);
        }
    }
}
