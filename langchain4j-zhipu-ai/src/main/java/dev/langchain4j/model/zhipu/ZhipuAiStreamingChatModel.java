package dev.langchain4j.model.zhipu;

import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.service.v4.model.ChatCompletionRequest;
import com.zhipu.oapi.service.v4.model.Choice;
import com.zhipu.oapi.service.v4.model.ModelApiResponse;
import com.zhipu.oapi.service.v4.model.ToolCalls;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.zhipu.spi.ZhipuAiStreamingChatModelBuilderFactory;
import lombok.Builder;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.zhipu.DefaultZhipuAiHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class ZhipuAiStreamingChatModel implements StreamingChatLanguageModel {

    private final Double temperature;
    private final Double topP;
    private final String model;
    private final Integer maxToken;
    private final ClientV4 client;

    @Builder
    public ZhipuAiStreamingChatModel(
            String apiKey,
            Double temperature,
            Double topP,
            String model,
            Integer maxToken
    ) {
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.model = getOrDefault(model, ZhipuAiChatModelName.GLM_4.toString());
        this.maxToken = getOrDefault(maxToken, 512);
        this.client = new ClientV4.Builder(apiKey).build();
    }

    public static ZhipuAiStreamingChatModelBuilder builder() {
        for (ZhipuAiStreamingChatModelBuilderFactory factories : loadFactories(ZhipuAiStreamingChatModelBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAiStreamingChatModelBuilder();
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

        ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
                .model(this.model)
                .stream(true)
                .temperature(this.temperature.floatValue())
                .maxTokens(this.maxToken)
                .messages(toZhipuAiMessages(messages))
                .toolChoice("auto");

        if (topP != null) {
            builder.topP(topP.floatValue());
        }

        if (!isNullOrEmpty(toolSpecifications)) {
            builder.tools(toTools(toolSpecifications));
        }

        ModelApiResponse response = client.invokeModelApi(builder.build());

        streamingChatCompletion(response, handler);
    }


    private void streamingChatCompletion(ModelApiResponse response, StreamingResponseHandler<AiMessage> handler) {
        final StringBuffer contentBuilder = new StringBuffer();
        AtomicReference<List<ToolExecutionRequest>> specifications = new AtomicReference<>();
        AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>();
        AtomicReference<FinishReason> finishReason = new AtomicReference<>();
        response.getFlowable()
                .doOnNext(item -> {
                    Choice choice = item.getChoices().get(0);
                    String chunk = choice.getDelta().getContent();
                    if (isNotNullOrEmpty(chunk)) {
                        contentBuilder.append(chunk);
                        handler.onNext(chunk);
                    }
                    if (item.getUsage() != null) {
                        tokenUsage.set(tokenUsageFrom(item.getUsage()));
                    }
                    if (choice.getFinishReason() != null) {
                        finishReason.set(finishReasonFrom(choice.getFinishReason()));
                    }
                    List<ToolCalls> toolCalls = choice.getDelta().getTool_calls();
                    if (!isNullOrEmpty(toolCalls)) {
                        specifications.set(specificationsFrom(toolCalls));
                    }
                })
                .doOnComplete(() -> {
                    AiMessage aiMessage;
                    if (isNullOrEmpty(specifications.get())) {
                        aiMessage = AiMessage.from(contentBuilder.toString());
                    } else {
                        aiMessage = AiMessage.from(specifications.get());
                    }
                    handler.onComplete(Response.from(
                            aiMessage,
                            tokenUsage.get(),
                            finishReason.get()
                    ));
                })
                .subscribe();
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        this.generate(messages, toolSpecification == null ? null : Collections.singletonList(toolSpecification), handler);
    }

    public static class ZhipuAiStreamingChatModelBuilder {
        public ZhipuAiStreamingChatModelBuilder() {

        }
    }
}
