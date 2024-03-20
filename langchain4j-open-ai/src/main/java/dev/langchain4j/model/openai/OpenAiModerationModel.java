package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.moderation.ModerationRequest;
import dev.ai4j.openai4j.moderation.ModerationResponse;
import dev.ai4j.openai4j.moderation.ModerationResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.spi.OpenAiModerationModelBuilderFactory;
import dev.langchain4j.model.output.Response;
import lombok.Builder;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.*;
import static dev.langchain4j.model.openai.OpenAiModelName.TEXT_MODERATION_LATEST;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents an OpenAI moderation model, such as text-moderation-latest.
 */
public class OpenAiModerationModel implements ModerationModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    @Builder
    public OpenAiModerationModel(String baseUrl,
                                 String apiKey,
                                 String organizationId,
                                 String modelName,
                                 Duration timeout,
                                 Integer maxRetries,
                                 Proxy proxy,
                                 Boolean logRequests,
                                 Boolean logResponses) {

        baseUrl = getOrDefault(baseUrl, OPENAI_URL);
        if (OPENAI_DEMO_API_KEY.equals(apiKey)) {
            baseUrl = OPENAI_DEMO_URL;
        }

        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = OpenAiClient.builder()
                .openAiApiKey(apiKey)
                .baseUrl(baseUrl)
                .organizationId(organizationId)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .build();
        this.modelName = getOrDefault(modelName, TEXT_MODERATION_LATEST);
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public Response<Moderation> moderate(String text) {
        return moderateInternal(singletonList(text));
    }

    private Response<Moderation> moderateInternal(List<String> inputs) {

        ModerationRequest request = ModerationRequest.builder()
                .model(modelName)
                .input(inputs)
                .build();

        ModerationResponse response = withRetry(() -> client.moderation(request).execute(), maxRetries);

        int i = 0;
        for (ModerationResult moderationResult : response.results()) {
            if (moderationResult.isFlagged()) {
                return Response.from(Moderation.flagged(inputs.get(i)));
            }
            i++;
        }

        return Response.from(Moderation.notFlagged());
    }

    @Override
    @SuppressWarnings("deprecation")
    public Response<Moderation> moderate(List<ChatMessage> messages) {
        List<String> inputs = messages.stream()
                .map(ChatMessage::text)
                .collect(toList());

        return moderateInternal(inputs);
    }

    public static OpenAiModerationModel withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static OpenAiModerationModelBuilder builder() {
        for (OpenAiModerationModelBuilderFactory factory : loadFactories(OpenAiModerationModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiModerationModelBuilder();
    }

    public static class OpenAiModerationModelBuilder {

        public OpenAiModerationModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public OpenAiModerationModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiModerationModelBuilder modelName(OpenAiModerationModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }
    }
}
