package dev.langchain4j.model.ark;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.service.ArkService;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.ark.spi.ArkStreamingChatModelBuilderFactory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.Builder;

import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.model.ark.ArkHelper.toArkMessages;
import static dev.langchain4j.model.ark.ArkHelper.toToolFunctions;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * Represents a Ark language model with a chat completion interface.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <br>
 * More details are available <a href="https://www.volcengine.com/docs/82379/1263512">here</a>.
 */
public class ArkStreamingChatModel implements StreamingChatLanguageModel {
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
    public ArkStreamingChatModel(String apiKey,
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
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generateModel(messages, null, null, handler);
    }

    private void generateModel(List<ChatMessage> messages,
                               List<ToolSpecification> toolSpecifications,
                               ToolSpecification toolThatMustBeExecuted,
                               StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");
        ensureNotNull(handler, "handler");

        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(model)
                .topP(topP)
                .frequencyPenalty(frequencyPenalty)
                .presencePenalty(presencePenalty)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .user(user)
                .streamOptions(ChatCompletionRequest.ChatCompletionRequestStreamOptions.of(true))
                .messages(toArkMessages(messages));
        if (stops != null) {
            builder.stop(stops);
        }
        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            builder.tools(toToolFunctions(toolSpecifications));
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
                                if (Utils.isNullOrEmpty(chunk.getChoices()) && responseBuilder.isFinish()) {
                                    handler.onComplete(responseBuilder.build());
                                }
                            }
                        }
                );

        // shutdown service
        service.shutdownExecutor();
    }


    public static ArkStreamingChatModelBuilder builder() {
        for (ArkStreamingChatModelBuilderFactory factory : loadFactories(ArkStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new ArkStreamingChatModelBuilder();
    }

    public static class ArkStreamingChatModelBuilder {
        public ArkStreamingChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
