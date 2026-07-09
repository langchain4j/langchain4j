package dev.langchain4j.model.openai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_OPENAI_URL;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.DEFAULT_USER_AGENT;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.time.Duration.ofSeconds;

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
import java.util.function.Supplier;
import org.slf4j.Logger;

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
                .logger(builder.logger)
                .userAgent(DEFAULT_USER_AGENT)
                .customHeaders(builder.customHeadersSupplier)
                .customQueryParams(builder.customQueryParams)
                .build();
        this.modelName = builder.modelName;
        this.temperature = builder.temperature;
    }

    public String modelName() {
        return modelName;
    }

    @Override
    public void generate(String prompt, StreamingResponseHandler<String> handler) {

        CompletionRequest request = CompletionRequest.builder().stream(true)
                .streamOptions(StreamOptions.builder().includeUsage(true).build())
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
                            chatResponse.metadata().finishReason()));
                })
                .onError(throwable -> {
                    handler.onError(ExceptionMapper.DEFAULT.mapException(throwable));
                })
                .execute();
    }

    public static OpenAiStreamingLanguageModelBuilder builder() {
        for (OpenAiStreamingLanguageModelBuilderFactory factory :
                loadFactories(OpenAiStreamingLanguageModelBuilderFactory.class)) {
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
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private Map<String, String> customQueryParams;

        public OpenAiStreamingLanguageModelBuilder() {
            // This is public so it can be extended
        }

        /**
         * Sets a custom {@link HttpClientBuilder} used to create the HTTP client.
         * Allows full control over timeouts, proxy settings, and other HTTP client options.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets the model name, e.g. {@code "gpt-3.5-turbo-instruct"}.
         *
         * @param modelName the model name
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the model name using a {@link OpenAiLanguageModelName} enum constant.
         *
         * @param modelName the model name enum value
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder modelName(OpenAiLanguageModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * Sets the base URL of the OpenAI-compatible API. Defaults to {@code https://api.openai.com/v1}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the OpenAI API key used to authenticate requests.
         *
         * @param apiKey the API key
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the OpenAI organization ID sent with each request.
         *
         * @param organizationId the organization ID
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder organizationId(String organizationId) {
            this.organizationId = organizationId;
            return this;
        }

        /**
         * Sets the OpenAI project ID sent with each request.
         *
         * @param projectId the project ID
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        /**
         * Sets the sampling temperature in the range {@code [0.0, 2.0]}.
         * Higher values produce more varied output; lower values are more deterministic.
         *
         * @param temperature the sampling temperature
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the HTTP request timeout. Defaults to 15 seconds for connect and 60 seconds for read.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Enables or disables logging of HTTP request bodies for debugging. Defaults to {@code false}.
         *
         * @param logRequests whether to log requests
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables or disables logging of HTTP response bodies for debugging. Defaults to {@code false}.
         *
         * @param logResponses whether to log responses
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public OpenAiStreamingLanguageModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public OpenAiStreamingLanguageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         * For example, this is useful for OAuth2 tokens that expire and need refreshing.
         */
        public OpenAiStreamingLanguageModelBuilder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Sets additional URL query parameters appended to every request.
         *
         * @param customQueryParams the query parameters map
         * @return {@code this}
         */
        public OpenAiStreamingLanguageModelBuilder customQueryParams(Map<String, String> customQueryParams) {
            this.customQueryParams = customQueryParams;
            return this;
        }

        public OpenAiStreamingLanguageModel build() {
            return new OpenAiStreamingLanguageModel(this);
        }
    }
}
