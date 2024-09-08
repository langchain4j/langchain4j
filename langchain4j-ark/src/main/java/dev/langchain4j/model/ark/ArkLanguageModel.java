package dev.langchain4j.model.ark;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;

import dev.langchain4j.model.ark.spi.ArkLanguageModelBuilderFactory;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.ark.ArkHelper.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Ark language model with a chat completion interface.
 * More details are available <a href="https://www.volcengine.com/docs/82379/1263512">here</a>.
 */
public class ArkLanguageModel implements LanguageModel {
    private final String apiKey;
    private final String model;
    private final Double topP;
    private final Double frequencyPenalty;
    private final Double presencePenalty;
    private final Double temperature;
    private final List<String> stops;
    private final Integer maxTokens;
    private final String user;
    private final Integer maxRetries;
    private final ArkService service;

    @Builder
    public ArkLanguageModel(String apiKey,
                            String model,
                            Double topP,
                            Double frequencyPenalty,
                            Double presencePenalty,
                            Double temperature,
                            List<String> stops,
                            Integer maxTokens,
                            String user,
                            Integer maxRetries) {
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
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.service = new ArkService(apiKey);
    }

    @Override
    public Response<String> generate(String prompt) {
        ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(prompt).build();
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(model)
                .topP(topP)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .user(user)
                .messages(Collections.singletonList(userMessage));
        if (stops != null) {
            builder.stop(stops);
        }
        ChatCompletionResult chatCompletionResult = withRetry(() -> service.createChatCompletion(builder.build()), maxRetries);

        // shutdown service
        // service.shutdownExecutor();
        return Response.from(answerFrom(chatCompletionResult),
                tokenUsageFrom(chatCompletionResult), finishReasonFrom(chatCompletionResult));
    }

    public static ArkLanguageModelBuilder builder() {
        for (ArkLanguageModelBuilderFactory factory : loadFactories(ArkLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new ArkLanguageModelBuilder();
    }

    public static class ArkLanguageModelBuilder {
        public ArkLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
