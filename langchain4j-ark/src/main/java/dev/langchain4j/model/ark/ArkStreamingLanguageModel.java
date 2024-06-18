package dev.langchain4j.model.ark;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.ark.spi.ArkStreamingLanguageModelBuilderFactory;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Ark language model with a text interface.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <br>
 * More details are available <a href="https://www.volcengine.com/docs/82379/1263512">here</a>.
 */
public class ArkStreamingLanguageModel implements StreamingLanguageModel {
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
    public ArkStreamingLanguageModel(String apiKey,
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
    public void generate(String prompt, StreamingResponseHandler<String> handler) {
        ensureNotBlank(prompt, "prompt");

        ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(prompt).build();
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(model)
                .topP(topP)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .user(user)
                .streamOptions(ChatCompletionRequest.ChatCompletionRequestStreamOptions.of(true))
                .messages(Collections.singletonList(userMessage));
        if (stops != null) {
            builder.stop(stops);
        }
        ArkStreamingResponseBuilder responseBuilder = new ArkStreamingResponseBuilder();

        service.streamChatCompletion(builder.build())
                .doOnError(e -> handler.onError(e))
                .doOnComplete(() -> {
                })
                .blockingForEach(
                        chunk -> {
                            String delta = responseBuilder.append(chunk);
                            if (Utils.isNotNullOrBlank(delta)) {
                                handler.onNext(delta);
                            } else {
                                Response<AiMessage> response = responseBuilder.build();
                                if (Utils.isNullOrEmpty(chunk.getChoices()) && responseBuilder.isFinish()) {
                                    handler.onComplete(Response.from(
                                            response.content().text(),
                                            response.tokenUsage(),
                                            response.finishReason()
                                    ));
                                }
                            }
                        }
                );

        // shutdown service
        service.shutdownExecutor();
    }

    public static ArkStreamingLanguageModelBuilder builder() {
        for (ArkStreamingLanguageModelBuilderFactory factory : loadFactories(ArkStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new ArkStreamingLanguageModelBuilder();
    }

    public static class ArkStreamingLanguageModelBuilder {
        public ArkStreamingLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
