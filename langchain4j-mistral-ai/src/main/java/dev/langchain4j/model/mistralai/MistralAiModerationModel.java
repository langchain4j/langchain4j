package dev.langchain4j.model.mistralai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModerationRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModerationResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralCategories;
import dev.langchain4j.model.mistralai.internal.api.MistralModerationResult;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.singletonList;

public class MistralAiModerationModel implements ModerationModel {

    private final MistralAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    public MistralAiModerationModel(String baseUrl,
                                    String apiKey,
                                    Duration timeout,
                                    Integer maxRetries,
                                    String modelName,
                                    Boolean logRequests,
                                    Boolean logResponses) {

        this.client = MistralAiClient.builder()
            .baseUrl(getOrDefault(baseUrl, "https://api.mistral.ai/v1"))
            .apiKey(apiKey)
            .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
            .logRequests(getOrDefault(logRequests, false))
            .logResponses(getOrDefault(logResponses, false))
            .build();

        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxRetries = getOrDefault(maxRetries, 3);
    }


    @Override
    public Response<Moderation> moderate(String text) {
        return moderateInternal(singletonList(text));
    }

    @Override
    public Response<Moderation> moderate(List<ChatMessage> messages) {
        return moderateInternal(messages.stream().map(ChatMessage::text).collect(Collectors.toUnmodifiableList()));
    }


    private Response<Moderation> moderateInternal(List<String> inputs) {

        MistralAiModerationRequest request = new MistralAiModerationRequest(modelName, inputs);

        MistralAiModerationResponse response = withRetry(() -> client.moderation(request), maxRetries);

        int i = 0;
        for (MistralModerationResult moderationResult : response.results()) {

            if (isAnyCategoryFlagged(moderationResult.getCategories())) {
                return Response.from(Moderation.flagged(inputs.get(i)));
            }
            i++;
        }

        return Response.from(Moderation.notFlagged());
    }


    private boolean isAnyCategoryFlagged(MistralCategories categories) {
        return (categories.getSexual() != null && categories.getSexual()) ||
            (categories.getHateAndDiscrimination() != null && categories.getHateAndDiscrimination()) ||
            (categories.getViolenceAndThreats() != null && categories.getViolenceAndThreats()) ||
            (categories.getDangerousAndCriminalContent() != null && categories.getDangerousAndCriminalContent()) ||
            (categories.getSelfHarm() != null && categories.getSelfHarm()) ||
            (categories.getHealth() != null && categories.getHealth()) ||
            (categories.getLaw() != null && categories.getLaw()) ||
            (categories.getPii() != null && categories.getPii());
    }

    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private String modelName;
        private Integer maxRetries;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MistralAiModerationModel build() {
            return new MistralAiModerationModel(baseUrl, apiKey, timeout, maxRetries, modelName, logRequests, logResponses);
        }
    }
}
