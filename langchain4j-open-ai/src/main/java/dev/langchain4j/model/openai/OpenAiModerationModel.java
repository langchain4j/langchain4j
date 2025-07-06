package dev.langchain4j.model.openai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.moderation.ModerationRequest;
import dev.langchain4j.model.openai.internal.moderation.ModerationResponse;
import dev.langchain4j.model.openai.internal.moderation.ModerationResult;
import dev.langchain4j.model.openai.spi.OpenAiModerationModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

/**
 * Represents an OpenAI moderation model, such as text-moderation-latest.
 */
public class OpenAiModerationModel implements ModerationModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Integer maxRetries;

    public OpenAiModerationModel(OpenAiModerationModelBuilder builder) {

        this.client = OpenAiClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_OPENAI_URL))
                .apiKey(builder.apiKey)
                .organizationId(builder.organizationId)
                .projectId(builder.projectId)
                .connectTimeout(getOrDefault(builder.timeout, ofSeconds(15)))
                .readTimeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(builder.customHeaders)
                .build();
        this.modelName = builder.modelName;
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
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

        ModerationResponse response = withRetryMappingExceptions(() -> client.moderation(request).execute(), maxRetries);

        int i = 0;
        for (ModerationResult moderationResult : response.results()) {
            if (Boolean.TRUE.equals(moderationResult.isFlagged())) {
                return Response.from(Moderation.flagged(inputs.get(i)));
            }
            i++;
        }

        return Response.from(Moderation.notFlagged());
    }

    @Override
    public Response<Moderation> moderate(List<ChatMessage> messages) {
        List<String> inputs = messages.stream()
                .map(OpenAiModerationModel::toText)
                .toList();

        return moderateInternal(inputs);
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

    public static OpenAiModerationModelBuilder builder() {
        for (OpenAiModerationModelBuilderFactory factory : loadFactories(OpenAiModerationModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiModerationModelBuilder();
    }

    public static class OpenAiModerationModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;

        private String modelName;
        private Duration timeout;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public OpenAiModerationModelBuilder() {
            // This is public so it can be extended
        }

        public OpenAiModerationModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OpenAiModerationModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiModerationModelBuilder modelName(OpenAiModerationModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiModerationModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiModerationModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiModerationModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiModerationModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public OpenAiModerationModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiModerationModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public OpenAiModerationModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OpenAiModerationModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OpenAiModerationModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiModerationModel build() {
            return new OpenAiModerationModel(this);
        }
    }
}
