package dev.langchain4j.model.openai;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.completion.CompletionChoice;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.shared.StreamOptions;
import dev.langchain4j.model.openai.spi.OpenAiStreamingLanguageModelBuilderFactory;
import dev.langchain4j.model.output.Response;

import java.time.Duration;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

/**
 * Represents an OpenAI language model with a completion interface, such as gpt-3.5-turbo-instruct.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * However, it's recommended to use {@link OpenAiStreamingChatModel} instead,
 * as it offers more advanced features like function calling, multi-turn conversations, etc.
 */
public class OpenAiStreamingLanguageModel implements StreamingLanguageModel {

    private final OpenAiClient client;
    private final String modelName;
    private final Double temperature;

    public OpenAiStreamingLanguageModel(OpenAiStreamingLanguageModelBuilder builder) {
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
        this.temperature = builder.temperature;
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {

        CompletionRequest request = CompletionRequest.builder()
                .stream(true)
                .streamOptions(StreamOptions.builder()
                        .includeUsage(true)
                        .build())
                .model(modelName)
                .prompt(prompt)
                .temperature(temperature)
                .build();

        OpenAiStreamingResponseBuilder responseBuilder = new OpenAiStreamingResponseBuilder();

        client.completion(request)
                .onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    for (CompletionChoice choice : partialResponse.choices()) {
                        String token = choice.text();
                        if (isNotNullOrEmpty(token)) {
                            handler.onNext(token);
                        }
                    }
                })
                .onComplete(() -> {
                    ChatResponse chatResponse = responseBuilder.build();
                    handler.onComplete(Response.from(
                            chatResponse.aiMessage().text(),
                            chatResponse.metadata().tokenUsage(),
                            chatResponse.metadata().finishReason()
                    ));
                })
                .onError(throwable -> {
                    handler.onError(ExceptionMapper.DEFAULT.mapException(throwable));
                })
                .execute();
    }

    public static OpenAiStreamingLanguageModelBuilder builder() {
        for (OpenAiStreamingLanguageModelBuilderFactory factory : loadFactories(OpenAiStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new OpenAiStreamingLanguageModelBuilder();
    }

    public static class OpenAiStreamingLanguageModelBuilder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private String organizationId;
        private String projectId;

        private String modelName;
        private Double temperature;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public OpenAiStreamingLanguageModelBuilder() {
            // This is public so it can be extended
        }

        public OpenAiStreamingLanguageModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder modelName(OpenAiLanguageModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public OpenAiStreamingLanguageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public OpenAiStreamingLanguageModel build() {
            return new OpenAiStreamingLanguageModel(this);
        }
    }
}
