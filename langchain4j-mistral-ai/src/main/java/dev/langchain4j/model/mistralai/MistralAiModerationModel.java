package dev.langchain4j.model.mistralai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.singletonList;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.mistralai.internal.api.MistralAiCategories;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModerationRequest;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModerationResponse;
import dev.langchain4j.model.mistralai.internal.api.MistralAiModerationResult;
import dev.langchain4j.model.mistralai.internal.client.MistralAiClient;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;

public class MistralAiModerationModel implements ModerationModel {

    private final MistralAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    /**
     * Constructs a MistralAiModerationModel with the specified parameters.
     *
     * @param httpClientBuilder the HTTP client builder to use for creating the HTTP client
     * @param baseUrl the base URL of the Mistral AI API. It uses the default value if not specified
     * @param apiKey the API key for authentication
     * @param timeout the timeout duration for API requests
     * @param maxRetries the maximum number of retries for API requests
     * @param modelName the name of the Mistral AI model to use
     * @param logRequests a flag indicating whether to log API requests
     * @param logResponses a flag indicating whether to log API responses
     */
    public MistralAiModerationModel(
            HttpClientBuilder httpClientBuilder,
            String baseUrl,
            String apiKey,
            Duration timeout,
            Integer maxRetries,
            String modelName,
            Boolean logRequests,
            Boolean logResponses) {

        this.client = MistralAiClient.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(getOrDefault(baseUrl, "https://api.mistral.ai/v1"))
                .apiKey(apiKey)
                .timeout(getOrDefault(timeout, Duration.ofSeconds(60)))
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();

        this.modelName = ensureNotBlank(modelName, "modelName");
        this.maxRetries = getOrDefault(maxRetries, 2);
    }

    /**
     * Constructs a MistralAiModerationModel with the specified parameters.
     * @deprecated Please use {@link #MistralAiModerationModel(HttpClientBuilder, String, String, Duration, Integer, String, Boolean, Boolean)} instead.
     *
     * @param baseUrl the base URL of the Mistral AI API. It uses the default value if not specified
     * @param apiKey the API key for authentication
     * @param timeout the timeout duration for API requests
     * @param maxRetries the maximum number of retries for API requests
     * @param modelName the name of the Mistral AI model to use
     * @param logRequests a flag indicating whether to log API requests
     * @param logResponses a flag indicating whether to log API responses
     */
    @Deprecated(forRemoval = true)
    public MistralAiModerationModel(
            String baseUrl,
            String apiKey,
            Duration timeout,
            Integer maxRetries,
            String modelName,
            Boolean logRequests,
            Boolean logResponses) {
        this(null, baseUrl, apiKey, timeout, maxRetries, modelName, logRequests, logResponses);
    }

    @Override
    public Response<Moderation> moderate(String text) {
        return moderateInternal(singletonList(text));
    }

    @Override
    public Response<Moderation> moderate(List<ChatMessage> messages) {
        return moderateInternal(
                messages.stream().map(MistralAiModerationModel::toText).toList());
    }

    private static String toText(ChatMessage chatMessage) {
        if (chatMessage instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (chatMessage instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else if (chatMessage instanceof AiMessage aiMessage) {
            return aiMessage.text();
        } else if (chatMessage instanceof ToolExecutionResultMessage toolExecutionResultMessage) {
            return toolExecutionResultMessage.text();
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + chatMessage.type());
        }
    }

    private Response<Moderation> moderateInternal(List<String> inputs) {

        MistralAiModerationRequest request = new MistralAiModerationRequest(modelName, inputs);

        MistralAiModerationResponse response = withRetryMappingExceptions(() -> client.moderation(request), maxRetries);

        int i = 0;
        for (MistralAiModerationResult moderationResult : response.results()) {

            if (isAnyCategoryFlagged(moderationResult.getCategories())) {
                return Response.from(Moderation.flagged(inputs.get(i)));
            }
            i++;
        }

        return Response.from(Moderation.notFlagged());
    }

    private boolean isAnyCategoryFlagged(MistralAiCategories categories) {
        return (categories.getSexual() != null && categories.getSexual())
                || (categories.getHateAndDiscrimination() != null && categories.getHateAndDiscrimination())
                || (categories.getViolenceAndThreats() != null && categories.getViolenceAndThreats())
                || (categories.getDangerousAndCriminalContent() != null && categories.getDangerousAndCriminalContent())
                || (categories.getSelfHarm() != null && categories.getSelfHarm())
                || (categories.getHealth() != null && categories.getHealth())
                || (categories.getLaw() != null && categories.getLaw())
                || (categories.getPii() != null && categories.getPii());
    }

    public static class Builder {
        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private String modelName;
        private Integer maxRetries;
        private HttpClientBuilder httpClientBuilder;

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

        /**
         * @param httpClientBuilder the HTTP client builder to use for creating the HTTP client
         * @return {@code this}.
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public MistralAiModerationModel build() {
            return new MistralAiModerationModel(
                    this.httpClientBuilder,
                    this.baseUrl,
                    this.apiKey,
                    this.timeout,
                    this.maxRetries,
                    this.modelName,
                    this.logRequests,
                    this.logResponses);
        }
    }
}
