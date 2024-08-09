package dev.langchain4j.model.ark;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ark.spi.ArkChatModelBuilderFactory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.util.List;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.service.ArkService;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.ark.ArkHelper.finishReasonFrom;
import static dev.langchain4j.model.ark.ArkHelper.tokenUsageFrom;
import static dev.langchain4j.model.ark.ArkHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Ark language model with a chat completion interface.
 * More details are available <a href="https://www.volcengine.com/docs/82379/1263512">here</a>.
 */
public class ArkChatModel implements ChatLanguageModel {
    private final String apiKey;
    private final String model;
    private final Double topP;
    private final Double frequencyPenalty;
    private final Double presencePenalty;
    private final Double temperature;
    private final List<String> stops;
    private final Integer maxTokens;
    private final String user;
    private final ArkService service;

    @Builder
    protected ArkChatModel(String apiKey,
                           String model,
                           Double topP,
                           Double frequencyPenalty,
                           Double presencePenalty,
                           Double temperature,
                           List<String> stops,
                           Integer maxTokens,
                           String user) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("Ark api key must be defined. It can be generated here: https://www.volcengine.com/docs/82379/1263279");
        }
        if (isNullOrBlank(model)) {
            throw new IllegalArgumentException("Ark model(endpoint_id) must be defined. ");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.topP = topP;
        this.frequencyPenalty = frequencyPenalty;
        this.presencePenalty = presencePenalty;
        this.temperature = temperature;
        this.stops = stops;
        this.maxTokens = maxTokens;
        this.user = user;
        this.service = new ArkService(apiKey);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generateModel(messages, null, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generateModel(messages, toolSpecifications, null);
    }

    private Response<AiMessage> generateModel(
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted
    ) {
        ensureNotEmpty(messages, "messages");
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(model)
                .topP(topP)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .user(user)
                .messages(toArkMessages(messages));
        if (stops != null) {
            builder.stop(stops);
        }
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            builder.tools(toToolFunctions(toolSpecifications));
        }
        ChatCompletionResult chatCompletionResult = service.createChatCompletion(builder.build());

        // shutdown service
        service.shutdownExecutor();
        return Response.from(
                aiMessageFrom(chatCompletionResult),
                tokenUsageFrom(chatCompletionResult),
                finishReasonFrom(chatCompletionResult)
        );
    }

    public static ArkChatModelBuilder builder() {
        for (ArkChatModelBuilderFactory factory : loadFactories(ArkChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new ArkChatModelBuilder();
    }

    public static class ArkChatModelBuilder {
        public ArkChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
